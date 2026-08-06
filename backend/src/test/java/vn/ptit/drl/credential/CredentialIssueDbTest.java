package vn.ptit.drl.credential;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import vn.ptit.drl.anchor.AnchorDomain;
import vn.ptit.drl.anchor.Jcs;
import vn.ptit.drl.anchor.LeafHasher;
import vn.ptit.drl.identity.Student;
import vn.ptit.drl.identity.StudentRepository;
import vn.ptit.drl.org.OrgType;
import vn.ptit.drl.org.Organization;
import vn.ptit.drl.org.OrganizationRepository;

/**
 * Cấp credential THẬT xuống MySQL — luồng đầy đủ, không mock.
 *
 * <p>Chạy: {@code .\scripts\test-backend.ps1 CredentialIssueDbTest} (cần container MySQL ở
 * cổng 3310 — chạy {@code mvnw} trực tiếp sẽ nối nhầm vào MySQL của Windows ở 3306).
 *
 * <p><b>Vì sao cần test chạm CSDL thật, khi đã có vector test thuần.</b> Bộ vector chốt công
 * thức và hợp đồng với verifier, nhưng nó dựng {@link Credential} bằng {@code builder()} nên
 * không đi qua Hibernate. Ba lớp lỗi chỉ lộ ra ở đây:
 *
 * <ul>
 *   <li>cột {@code NOT NULL} không có giá trị mặc định mà entity <b>không ánh xạ</b> — đúng
 *       loại lỗi mà {@code payload_hash} gây ra và V5 phải bỏ đi;
 *   <li>{@code updatable = false} chặn luôn cả lần ghi bằng chứng đầu tiên, làm credential
 *       lưu xuống với leaf và chữ ký toàn byte {@code 0x00};
 *   <li>{@code DATETIME(3)} đọc ra khác giá trị đã hash.
 * </ul>
 *
 * <p>Toàn bộ chạy trong giao dịch được cuộn lại, nên nó <b>không</b> để lại rác trong CSDL
 * phát triển và không đụng tới lô đã neo thật.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    // Khóa TEST công khai (tài khoản #0 của Hardhat). Đặt ở đây để test không phụ thuộc
    // ISSUER_PRIVATE_KEY trong .env — máy nào chạy cũng ra cùng địa chỉ ví.
    "drl.credential.issuer-private-key="
        + "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80",
    // Pool nhỏ để test cấp lại khi đụng chỉ số đã dùng chạy được trong thời gian hợp lý.
    "drl.credential.status-list-pool-size=4096"
})
class CredentialIssueDbTest {

  private static final String TEST_ISSUER_ADDRESS = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

  @Autowired CredentialService service;
  @Autowired CredentialRepository repository;
  @Autowired StudentRepository studentRepository;
  @Autowired OrganizationRepository organizationRepository;
  @Autowired JdbcTemplate jdbc;
  @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;

  private Student student;
  private Organization org;

  private void seed() {
    org = organizationRepository.save(Organization.builder()
        .name("Đoàn Thanh niên — dữ liệu test")
        .type(OrgType.DOAN)
        .build());

    student = studentRepository.save(Student.builder()
        .mssv("TEST" + System.nanoTime() % 100000000L)
        .fullName("Nguyễn Ngọc Hoàng")
        .classCode("D21CQCN01-B")
        .build());
  }

  private CredentialService.Request request() {
    return new CredentialService.Request(student, org, "2026-1", 12, 85, null);
  }

  // ------------------------------------------------------------------ luồng chính

  @Test
  @DisplayName("Cấp credential: mọi cột bắt buộc được ghi, không cột nào còn chỗ giữ chỗ")
  void capDuocVaGhiDuBangChung() {
    seed();
    Credential c = service.issue(request());

    assertNotNull(c.getId());
    assertEquals(student.getMssv(), c.getStudentCode(), "MSSV phải được CHỤP ẢNH");
    assertEquals("Nguyễn Ngọc Hoàng", c.getStudentName());
    assertEquals(TEST_ISSUER_ADDRESS, c.getIssuerAddress());
    assertEquals(CredentialType.HOAT_DONG, c.getType());
    assertNotNull(c.getStatusListIndex());

    // Đây là phép kiểm quan trọng nhất của lớp này. Nếu `updatable = false` chặn luôn lần
    // ghi bằng chứng đầu tiên thì hai giá trị này vẫn là chỗ giữ chỗ toàn 0x00, credential
    // trông vẫn "cấp thành công", và chỉ nhà tuyển dụng mới phát hiện ra khi verify.
    assertNotEquals(0, countNonZero(c.getLeafHash()), "leaf_hash vẫn là chỗ giữ chỗ toàn 0x00");
    assertNotEquals(0, countNonZero(c.getSignature()), "signature vẫn là chỗ giữ chỗ toàn 0x00");
    assertEquals(32, c.getLeafHash().length);
    assertEquals(IssuerSigner.SIGNATURE_BYTES, c.getSignature().length);
    assertNotEquals("{}", c.getPayloadJson());
  }

  @Test
  @DisplayName("Chữ ký phục hồi ra đúng địa chỉ ghi trong payload")
  void chuKyKhopDiaChiTrongPayload() {
    seed();
    Credential c = service.issue(request());

    assertEquals(c.getIssuerAddress(),
        IssuerSigner.recoverAddress(c.getLeafHash(), c.getSignature()),
        "Địa chỉ phục hồi khác issuerAddress đã neo — credential này vô dụng với verifier.");
  }

  @Test
  @DisplayName("Leaf lưu xuống CSDL khớp leaf tính lại từ payload đã lưu")
  void leafKhopPayloadDaLuu() {
    seed();
    Credential c = service.issue(request());

    Map<String, Object> payload = CredentialPayload.of(c);
    assertEquals(c.getPayloadJson(), Jcs.canonicalize(payload));
    assertArrayEquals(c.getLeafHash(), LeafHasher.leaf(AnchorDomain.CRED, payload));

    // Cùng phép kiểm mà job neo chạy trước khi neo — gọi trực tiếp để nó có test riêng.
    assertArrayEquals(c.getLeafHash(), CredentialService.recomputeAndVerifyLeaf(c));
  }

  @Test
  @DisplayName("Đọc lại từ CSDL cho đúng byte đã ghi — kiểu cột không được chuẩn hóa lại")
  void docLaiTuCsdlVanKhop() {
    seed();
    Long id = service.issue(request()).getId();

    // `entityManager.clear()` là phần QUAN TRỌNG NHẤT của test này, không phải chi tiết dọn
    // dẹp. Thiếu nó thì `findById` trả về chính thể hiện còn trong persistence context, tức
    // là so sánh giá trị trong bộ nhớ với chính nó — test xanh mà không kiểm gì cả.
    //
    // Bản đầu của test này thiếu đúng dòng đó, và vì thế nó KHÔNG phát hiện ra rằng cột
    // `payload_json` kiểu JSON của MySQL sắp xếp lại khóa và chèn khoảng trắng. Lỗi đó chỉ
    // lộ ra ở CredentialBundleDbTest, nơi dữ liệu được gieo bằng SQL thuần. Xem migration V6.
    repository.flush();
    entityManager.clear();

    Credential doclai = repository.findById(id).orElseThrow();

    assertEquals(
        vn.ptit.drl.anchor.Jcs.canonicalize(CredentialPayload.of(doclai)),
        doclai.getPayloadJson(),
        "payload_json đọc từ CSDL khác chuỗi đã ký. Nghi ngờ đầu tiên: kiểu cột đang là JSON"
            + " thay vì LONGTEXT — MySQL chuẩn hóa lại nội dung cột JSON. Xem V6.");

    assertArrayEquals(doclai.getLeafHash(), CredentialService.recomputeAndVerifyLeaf(doclai),
        "Giá trị đọc ra từ CSDL dựng lại payload KHÁC payload đã ký. Nghi ngờ đầu tiên:"
            + " phần mili giây của issued_at.");
    assertEquals(doclai.getIssuerAddress(),
        IssuerSigner.recoverAddress(doclai.getLeafHash(), doclai.getSignature()));
  }

  // ------------------------------------------------------------------ bất biến

  @Test
  @DisplayName("Không ghi đè được bằng chứng lần thứ hai — saveProof chỉ chạy đúng một lần")
  void khongKyLaiDuoc() {
    seed();
    Credential c = service.issue(request());

    // Bắt RuntimeException chứ không bắt thẳng IllegalStateException: Spring Data bọc mọi
    // ngoại lệ ném ra từ tầng repository qua PersistenceExceptionTranslationInterceptor, nên
    // cái đi ra ngoài là InvalidDataAccessApiUsageException. Thông điệp mới là thứ chốt
    // được — nó chứng minh chốt chặn của saveProof đã chặn, chứ không phải một lỗi khác.
    RuntimeException e = assertThrows(RuntimeException.class,
        () -> repository.saveProof(c.getId(), "{}", new byte[32], new byte[65]));
    assertTrue(e.getMessage() != null && e.getMessage().contains("KHÔNG được ký lại"),
        "Ngoại lệ không phải do chốt chặn saveProof: " + e);
  }

  @Test
  @DisplayName("Cột trong payload khai updatable=false — UPDATE của Hibernate không đụng tới")
  void cotTrongPayloadKhongSuaDuoc() {
    seed();
    Credential c = service.issue(request());
    String tenGoc = c.getStudentName();

    c.setStudentName("Tên Bị Sửa Trộm");
    repository.saveAndFlush(c);
    jdbc.execute("SELECT 1");

    String tenTrongDb = jdbc.queryForObject(
        "SELECT student_name FROM credentials WHERE id = ?", String.class, c.getId());

    assertEquals(tenGoc, tenTrongDb,
        "student_name đã bị UPDATE xuống CSDL. Mọi Merkle proof của credential này sẽ fail"
            + " vĩnh viễn và fail im lặng — xem javadoc lớp Credential.");
  }

  // ------------------------------------------------------------------ chỉ số thu hồi

  @Test
  @DisplayName("status_list_index cấp NGẪU NHIÊN, không phải bộ đếm")
  void chiSoNgauNhien() {
    seed();
    List<Long> indexes = new java.util.ArrayList<>();
    for (int i = 1; i <= 6; i++) {
      indexes.add(service.issue(
          new CredentialService.Request(student, org, "20" + (20 + i) + "-1", i, i * 5, null))
          .getStatusListIndex());
    }

    assertEquals(6, indexes.stream().distinct().count(), "Chỉ số bị trùng: " + indexes);

    boolean tangDan = true;
    for (int i = 1; i < indexes.size(); i++) {
      if (indexes.get(i) <= indexes.get(i - 1)) {
        tangDan = false;
        break;
      }
    }
    assertTrue(!tangDan,
        "Dãy chỉ số tăng dần — nghi ngờ ai đó đã 'đơn giản hóa' thành bộ đếm. Cấp tuần tự"
            + " làm sự kiện StatusChanged(index) trên chuỗi công khai lộ thứ tự cấp phát"
            + " (PROJECT.md §2.3). Dãy nhận được: " + indexes);
  }

  @Test
  @DisplayName("Credential vừa cấp nằm trong danh sách chờ neo của miền CRED")
  void namTrongDanhSachChoNeo() {
    seed();
    Credential c = service.issue(request());
    repository.flush();

    // PageRequest phải import: trường `org` của lớp này che mất tên gói `org.…`, nên viết
    // `org.springframework.data.domain.PageRequest` ở đây là lỗi biên dịch.
    List<Long> choNeo = repository.findPendingAnchor(PageRequest.of(0, 100))
        .stream().map(Credential::getId).toList();

    assertTrue(choNeo.contains(c.getId()),
        "Credential mới cấp không được job neo nhặt lên — miền CRED sẽ không bao giờ có lô nào.");
  }

  private static int countNonZero(byte[] b) {
    int n = 0;
    for (byte x : b) {
      if (x != 0) n++;
    }
    return n;
  }
}
