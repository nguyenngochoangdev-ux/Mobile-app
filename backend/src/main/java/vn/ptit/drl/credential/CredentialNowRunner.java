package vn.ptit.drl.credential;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.ptit.drl.identity.Student;
import vn.ptit.drl.identity.StudentRepository;
import vn.ptit.drl.org.Organization;
import vn.ptit.drl.org.OrganizationRepository;

/**
 * Cấp credential cho một sinh viên <b>từ dữ liệu điểm danh thật</b>, rồi xuất bundle.
 *
 * <pre>
 *   .\scripts\credential-now.ps1 B21DCCN002          # cấp (hoặc dùng lại bản đã cấp)
 *   .\scripts\anchor-now.ps1                          # neo — GIAO DỊCH THẬT
 *   .\scripts\credential-now.ps1 B21DCCN002 -Bundle   # xuất bundle ra tệp
 * </pre>
 *
 * <p>Cùng lối với {@link vn.ptit.drl.anchor.AnchorNowRunner}: dùng profile chứ không phải
 * endpoint HTTP. Cấp credential là thao tác ký bằng khóa của tổ chức và sẽ được neo vĩnh viễn;
 * profile bắt phải cố ý gõ ra.
 *
 * <p><b>Chạy lại được (idempotent).</b> Đã có credential cho cặp (sinh viên, học kỳ) thì dùng
 * lại, không cấp bản thứ hai. Cấp trùng không bị cây Merkle bắt — mỗi credential có nonce
 * riêng nên hai bản nội dung giống hệt vẫn ra hai lá khác nhau — nên chốt chặn phải nằm ở đây.
 *
 * <h2>⚠️ Giới hạn phải nói rõ: chưa lọc theo học kỳ</h2>
 *
 * <p>Bảng {@code events} <b>không có cột học kỳ hay năm học</b>, nên runner này đếm
 * <b>toàn bộ</b> bản ghi điểm danh của sinh viên và gắn nhãn học kỳ do người chạy truyền vào.
 * Với dữ liệu demo (một đợt) thì đúng; với dữ liệu nhiều kỳ thì con số sẽ gộp.
 *
 * <p>Không tự chế mốc ngày để chia kỳ: một khoảng ngày đoán mò sẽ thành con số sai trong
 * credential đã ký và đã neo — thứ không sửa được. Thêm cột học kỳ cho {@code events} là việc
 * của tuần 5, cùng rule engine. <b>Ghi vào phần hạn chế của báo cáo.</b>
 */
@Component
@Profile("credential-now")
@RequiredArgsConstructor
@Slf4j
public class CredentialNowRunner implements ApplicationRunner {

  private final CredentialService service;
  private final CredentialBundleService bundleService;
  private final CredentialRepository repository;
  private final StudentRepository students;
  private final OrganizationRepository organizations;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final org.springframework.context.ConfigurableApplicationContext context;

  @Value("${drl.run.mssv:}")
  private String mssv;

  @Value("${drl.run.semester:2026-1}")
  private String semester;

  /** Mã tổ chức cấp phát. Mặc định {@code DOAN} — Đoàn Thanh niên. */
  @Value("${drl.run.issuer-org-type:DOAN}")
  private String issuerOrgType;

  /** Đường dẫn tệp bundle. Rỗng thì không xuất. */
  @Value("${drl.run.bundle-out:}")
  private String bundleOut;

  /**
   * Chạy xong thì <b>thoát</b>, không để web server nằm lại.
   *
   * <p>Runner chạy trong ứng dụng web nên khi {@code run()} trả về, Tomcat vẫn giữ tiến trình
   * sống mãi — script phải Ctrl+C mới thoát, và luồng ba bước (cấp → neo → xuất bundle) trở
   * thành ba lần bấm tay. Đóng context tường minh ở đây.
   *
   * <p>Trả về mã thoát khác 0 khi hỏng để script gọi biết mà dừng, thay vì đi tiếp sang bước
   * neo với một credential chưa cấp được.
   */
  @Override
  public void run(ApplicationArguments args) {
    int code = 0;
    try {
      code = thucHien();
    } catch (Exception e) {
      log.error("That bai", e);
      code = 1;
    } finally {
      final int exitCode = code;
      System.exit(org.springframework.boot.SpringApplication.exit(context, () -> exitCode));
    }
  }

  private int thucHien() throws Exception {
    log.info("=== Cap credential (profile credential-now) ===");

    if (mssv == null || mssv.isBlank()) {
      log.error("Thieu MSSV. Chay: .\\scripts\\credential-now.ps1 <MSSV>");
      return 1;
    }

    Student student = students.findByMssv(mssv).orElse(null);
    if (student == null) {
      log.error("Khong thay sinh vien co MSSV {}", mssv);
      return 1;
    }

    Organization org = timToChuc();
    if (org == null) {
      return 1;
    }

    Credential credential = repository
        .findByStudentIdAndSemesterAndType(student.getId(), semester, CredentialType.HOAT_DONG)
        .orElseGet(() -> capMoi(student, org));

    if (credential == null) {
      return 1;
    }

    inChiTiet(credential);

    if (bundleOut != null && !bundleOut.isBlank()) {
      return xuatBundle(credential);
    }
    log.info("Chua xuat bundle. Neo xong roi chay lai voi -Bundle.");
    return 0;
  }

  // ---------------------------------------------------------------- tổ chức

  /**
   * Tổ chức cấp phát, và ghi địa chỉ ví đã đăng ký on-chain vào bản ghi của nó.
   *
   * <p>Cột {@code organizations.issuer_address} có từ V1 nhưng chưa nơi nào điền. Điền ở đây
   * là nối nốt sợi dây: bản ghi tổ chức trong CSDL và mục của nó trong {@code IssuerRegistry}
   * trỏ về cùng một ví.
   */
  private Organization timToChuc() {
    List<Organization> ds = organizations.findAll().stream()
        .filter(o -> o.getType() != null && issuerOrgType.equals(o.getType().name()))
        .toList();

    if (ds.isEmpty()) {
      log.error("Khong thay to chuc nao co type = {}. Chay seeder truoc?", issuerOrgType);
      return null;
    }
    Organization org = ds.get(0);

    String vi = service.issuerAddress();
    if (org.getIssuerAddress() == null) {
      jdbc.update(
          "UPDATE organizations SET issuer_address = ?, on_chain_registered_at = ? WHERE id = ?",
          vi, java.sql.Timestamp.from(Instant.now()), org.getId());
      log.info("Da gan vi {} cho to chuc {} (id {})", vi, org.getName(), org.getId());
    } else if (!org.getIssuerAddress().equalsIgnoreCase(vi)) {
      log.warn("To chuc {} dang gan vi {} nhung khoa dang dung la {}."
              + " Credential se mang dia chi cua KHOA DANG DUNG.",
          org.getName(), org.getIssuerAddress(), vi);
    }
    return org;
  }

  // ---------------------------------------------------------------- cấp

  private Credential capMoi(Student student, Organization org) {
    // Đếm từ dữ liệu điểm danh THẬT. Xem javadoc đầu lớp về việc chưa lọc theo học kỳ.
    Map<String, Object> tong = jdbc.queryForMap("""
        SELECT COUNT(*) AS so_hoat_dong, COALESCE(SUM(e.points), 0) AS tong_diem
          FROM attendances a JOIN events e ON e.id = a.event_id
         WHERE a.student_id = ?
        """, student.getId());

    int soHoatDong = ((Number) tong.get("so_hoat_dong")).intValue();
    int tongDiem = ((Number) tong.get("tong_diem")).intValue();

    if (soHoatDong == 0) {
      log.error("Sinh vien {} chua co ban ghi diem danh nao. Khong cap credential rong.", mssv);
      return null;
    }

    // Chỉ số chất lượng dữ liệu — đúng thứ PROJECT.md §10 nêu làm đóng góp của đề tài.
    Long daXacMinh = jdbc.queryForObject(
        "SELECT COUNT(*) FROM attendances WHERE student_id = ? AND verified = TRUE",
        Long.class, student.getId());

    log.info("Sinh vien {} · {} · {} hoat dong · {} diem · {}/{} xac minh bang may",
        mssv, student.getFullName(), soHoatDong, tongDiem, daXacMinh, soHoatDong);

    return service.issue(new CredentialService.Request(
        student, org, semester, soHoatDong, tongDiem, null));
  }

  // ---------------------------------------------------------------- xuất

  private int xuatBundle(Credential c) throws Exception {
    CredentialBundleService.Bundle bundle;
    try {
      bundle = bundleService.build(c.getId());
    } catch (RuntimeException e) {
      log.error("Chua xuat duoc bundle: {}", e.getMessage());
      return 1;
    }

    Path path = Path.of(bundleOut).toAbsolutePath();
    Files.createDirectories(path.getParent());
    Files.writeString(path,
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(bundle) + "\n",
        StandardCharsets.UTF_8);

    log.info("Da ghi bundle -> {}", path);
    log.info("Xac minh doc lap:");
    log.info("  cd verifier && node scripts/verify-bundle.mjs \"{}\"", path);
    return 0;
  }

  private void inChiTiet(Credential c) {
    log.info("Credential {} · {} · hoc ky {}", c.getId(), c.getStudentCode(), c.getSemester());
    log.info("  {} hoat dong · {} diem", c.getActivityCount(), c.getTotalPoints());
    log.info("  vi ky        {}", c.getIssuerAddress());
    log.info("  leaf         0x{}", java.util.HexFormat.of().formatHex(c.getLeafHash()));
    log.info("  statusIndex  {}", c.getStatusListIndex());
    log.info("  payload      {}", c.getPayloadJson());
  }
}
