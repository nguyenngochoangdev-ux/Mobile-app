package vn.ptit.drl.scoring;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.ptit.drl.anchor.AnchorDomain;
import vn.ptit.drl.anchor.LeafHasher;
import vn.ptit.drl.attendance.Attendance;
import vn.ptit.drl.attendance.AttendancePayload;
import vn.ptit.drl.attendance.AttendanceRepository;
import vn.ptit.drl.audit.AuditJson;
import vn.ptit.drl.audit.AuditService;
import vn.ptit.drl.common.web.BusinessException;
import vn.ptit.drl.identity.Student;
import vn.ptit.drl.identity.StudentRepository;

/**
 * Chấm điểm rèn luyện cả khóa cho một học kỳ.
 *
 * <h2>Bốn bước, và chỗ nào từ chối chạy</h2>
 *
 * <ol>
 *   <li><b>Nạp bộ quy tắc</b> và kiểm nó — {@link RuleEvaluator#kiemBoQuyTac}. Có lỗi thì
 *       <b>dừng ngay, không chấm ai cả</b>. Kiểm một lần trước khi chấm rẻ hơn nhiều so với
 *       phát hiện ở sinh viên thứ 317.
 *   <li><b>Gom dữ liệu một truy vấn</b> cho cả khóa. Một truy vấn mỗi sinh viên là 500 vòng
 *       tới CSDL cho một việc chạy vài giây.
 *   <li><b>Chấm từng người</b>: dựng dữ kiện, đánh giá SpEL, tính {@code evidence_hash}.
 *   <li><b>Ghi</b> — một lượt chấm, N bản ghi điểm, một mắt xích nhật ký.
 * </ol>
 *
 * <h2>Chấm lại thì tạo lượt mới, không sửa lượt cũ</h2>
 *
 * <p>{@link Score} để mọi cột trong payload {@code updatable = false}. Điểm đã chấm là một
 * phát biểu đã (sắp) được neo; sửa nó làm leaf tính lại ra giá trị khác. Cùng mô hình với
 * credential và nhật ký. Lượt cũ nằm lại, và {@code scoredAt} trong payload phân biệt được
 * hai lượt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoringService {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HexFormat HEX = HexFormat.of();

  private final RulesetRepository rulesets;
  private final ScoreRunRepository runs;
  private final ScoreRepository scores;
  private final StudentRepository students;
  private final AttendanceRepository attendances;
  private final RuleEvaluator evaluator;
  private final AuditService audit;

  /** Kết quả một lượt chấm — số liệu đi thẳng vào chương 11. */
  public record KetQuaLuot(long runId, String semester, String rulesetVersion,
                           int soSinhVien, int soCoHoatDong, long milis,
                           Map<String, Integer> phanBoXepLoai,
                           int diemThapNhat, int diemCaoNhat, double diemTrungBinh) {}

  // ------------------------------------------------------------------ bộ quy tắc

  /**
   * Nạp bộ quy tắc từ {@code classpath:rulesets/<version>.json} vào CSDL nếu chưa có.
   *
   * <p>Nạp từ tệp chứ không nhúng vào mã: bộ quy tắc là <b>văn bản quy chế</b>, và người sửa
   * nó là người soạn quy chế, không phải lập trình viên. Đọc nguyên văn rồi băm luôn — không
   * định dạng lại, không phân tích rồi in ra, vì {@code ruleset_hash} cam kết vào <b>đúng
   * byte của tệp</b> ({@link RulesetPayload}).
   */
  @Transactional
  public Ruleset napBoQuyTac(String version) {
    String json = docTep("rulesets/" + version + ".json");
    RulesetDoc doc = phanTich(json);

    if (!version.equals(doc.version())) {
      throw new BusinessException(
          "Tên tệp và trường `version` bên trong không khớp: tệp " + version
              + ", nội dung " + doc.version() + ". Sửa một trong hai.");
    }

    return rulesets.findByVersionAndSemester(doc.version(), doc.semester())
        .map(cu -> {
          // Đã nạp rồi. Kiểm byte có đổi không — nếu đổi thì ai đó sửa văn bản quy chế đã
          // công bố, và mọi điểm đã chấm theo bản cũ trở nên không giải thích được.
          byte[] hashMoi = RulesetPayload.rulesetHash(json);
          if (!java.util.Arrays.equals(hashMoi, cu.getRulesetHash())) {
            throw new BusinessException(
                "Bộ quy tắc " + version + " ĐÃ NẠP nhưng nội dung tệp hiện tại KHÁC bản đã"
                    + " lưu.\n  đã lưu:    0x" + HEX.formatHex(cu.getRulesetHash())
                    + "\n  tệp hiện tại: " + RulesetPayload.rulesetHashHex(json)
                    + "\nSửa bộ quy tắc đã công bố là đổi câu chuyện giải thích những điểm đã"
                    + " chấm. Muốn đổi thì tạo version mới.");
          }
          return cu;
        })
        .orElseGet(() -> {
          Ruleset r = rulesets.save(Ruleset.builder()
              .version(doc.version())
              .semester(doc.semester())
              .jsonBody(json)
              .rulesetHash(RulesetPayload.rulesetHash(json))
              .nonce(HEX.parseHex(LeafHasher.newNonce().substring(2)))
              .effectiveFrom(Instant.now().truncatedTo(ChronoUnit.SECONDS))
              .build());

          log.info("Nap bo quy tac {} · hoc ky {} · hash {} · {} diem tu du lieu / {} mac dinh",
              doc.version(), doc.semester(), RulesetPayload.rulesetHashHex(json),
              doc.diemTuDuLieu(), doc.diemMacDinh());

          audit.record("RULESET_PUBLISH", "rulesets", r.getId(), null, null,
              AuditJson.of(
                  "version", doc.version(),
                  "semester", doc.semester(),
                  "rulesetHash", RulesetPayload.rulesetHashHex(json),
                  "diemTuDuLieu", (long) doc.diemTuDuLieu(),
                  "diemMacDinh", (long) doc.diemMacDinh()));
          return r;
        });
  }

  /** Đọc và phân tích nội dung JSON của một bộ quy tắc đã lưu. */
  public RulesetDoc doc(Ruleset r) {
    return phanTich(r.getJsonBody());
  }

  /** Bộ quy tắc mới nhất còn hiệu lực của một học kỳ. */
  @Transactional(readOnly = true)
  public java.util.Optional<Ruleset> rulesetCuaHocKy(String semester) {
    return rulesets.findFirstBySemesterOrderByEffectiveFromDesc(semester);
  }

  // ------------------------------------------------------------------ chấm

  /**
   * Chấm điểm toàn bộ sinh viên cho một học kỳ.
   *
   * @throws BusinessException nếu bộ quy tắc có lỗi — <b>không chấm ai cả</b>
   */
  @Transactional
  public KetQuaLuot chamHocKy(String semester, String rulesetVersion, Long actorId) {
    long batDau = System.nanoTime();

    Ruleset ruleset = napBoQuyTac(rulesetVersion);
    RulesetDoc doc = doc(ruleset);

    if (!semester.equals(doc.semester())) {
      throw new BusinessException(
          "Bộ quy tắc " + rulesetVersion + " dành cho học kỳ " + doc.semester()
              + ", không phải " + semester + ".");
    }

    List<String> loi = evaluator.kiemBoQuyTac(doc);
    if (!loi.isEmpty()) {
      throw new BusinessException(
          "Bộ quy tắc " + rulesetVersion + " có " + loi.size() + " lỗi — KHÔNG chấm ai cả:\n  "
              + String.join("\n  ", loi));
    }

    ScoreRun run = runs.save(ScoreRun.builder()
        .semester(semester)
        .ruleset(ruleset)
        .runAt(Instant.now().truncatedTo(ChronoUnit.SECONDS))
        .status(ScoreRun.Status.RUNNING)
        .build());

    // Một truy vấn cho cả khóa, rồi gom theo sinh viên trong bộ nhớ.
    Map<Long, List<Attendance>> theoSinhVien = new LinkedHashMap<>();
    for (Attendance a : attendances.findBySemesterForScoring(semester)) {
      theoSinhVien.computeIfAbsent(a.getStudent().getId(), k -> new ArrayList<>()).add(a);
    }

    List<Student> tatCa = students.findAll();
    List<Score> ketQua = new ArrayList<>(tatCa.size());
    Map<String, Integer> phanBo = new LinkedHashMap<>();
    int thapNhat = Integer.MAX_VALUE;
    int caoNhat = Integer.MIN_VALUE;
    long tongDiem = 0;
    int soCoHoatDong = 0;

    for (Student sv : tatCa) {
      List<Attendance> cua = theoSinhVien.getOrDefault(sv.getId(), List.of());
      if (!cua.isEmpty()) {
        soCoHoatDong++;
      }

      ScoringFacts facts = new ScoringFacts();
      List<String> leaves = new ArrayList<>(cua.size());
      for (Attendance a : cua) {
        facts.themLuot(a.getEvent().getCriteriaCode(),
            a.getEvent().getPoints() == null ? 0 : a.getEvent().getPoints(),
            Boolean.TRUE.equals(a.getVerified()));
        leaves.add(LeafHasher.leafHex(AnchorDomain.ATTEND, AttendancePayload.of(a)));
      }

      RuleEvaluator.KetQua kq = evaluator.danhGia(doc, facts);

      // Bộ quy tắc đã được kiểm trước khi vào vòng lặp, nên lỗi ở đây là bất thường thật.
      if (!kq.loi().isEmpty()) {
        throw new IllegalStateException(
            "Sinh viên " + sv.getMssv() + ": biểu thức lỗi dù bộ quy tắc đã qua kiểm — "
                + String.join("; ", kq.loi()));
      }

      ketQua.add(Score.builder()
          .run(run)
          .student(sv)
          .c1(kq.diemTheoTieuChi().getOrDefault("C1", 0))
          .c2(kq.diemTheoTieuChi().getOrDefault("C2", 0))
          .c3(kq.diemTheoTieuChi().getOrDefault("C3", 0))
          .c4(kq.diemTheoTieuChi().getOrDefault("C4", 0))
          .c5(kq.diemTheoTieuChi().getOrDefault("C5", 0))
          .total(kq.total())
          .classification(kq.xepLoai())
          .evidenceHash(EvidenceHasher.hash(leaves))
          .nonce(HEX.parseHex(LeafHasher.newNonce().substring(2)))
          .build());

      phanBo.merge(kq.xepLoai() == null ? "(khong xep loai)" : kq.xepLoai(), 1, Integer::sum);
      thapNhat = Math.min(thapNhat, kq.total());
      caoNhat = Math.max(caoNhat, kq.total());
      tongDiem += kq.total();
    }

    scores.saveAll(ketQua);
    run.setStatus(ScoreRun.Status.DONE);
    runs.save(run);

    long milis = (System.nanoTime() - batDau) / 1_000_000;

    audit.record("SCORE_RUN", "score_runs", run.getId(), actorId, null,
        AuditJson.of(
            "semester", semester,
            "rulesetVersion", rulesetVersion,
            "rulesetHash", "0x" + HEX.formatHex(ruleset.getRulesetHash()),
            "soSinhVien", (long) ketQua.size(),
            "soCoHoatDong", (long) soCoHoatDong,
            "milis", milis));

    log.info("Cham xong {} sinh vien hoc ky {} trong {} ms · {} nguoi co hoat dong · phan bo {}",
        ketQua.size(), semester, milis, soCoHoatDong, phanBo);

    return new KetQuaLuot(run.getId(), semester, rulesetVersion, ketQua.size(), soCoHoatDong,
        milis, Map.copyOf(phanBo),
        ketQua.isEmpty() ? 0 : thapNhat,
        ketQua.isEmpty() ? 0 : caoNhat,
        ketQua.isEmpty() ? 0 : (double) tongDiem / ketQua.size());
  }

  // ------------------------------------------------------------------ tiện ích

  private static RulesetDoc phanTich(String json) {
    try {
      return MAPPER.readValue(json, RulesetDoc.class);
    } catch (Exception e) {
      throw new BusinessException("Bộ quy tắc không phân tích được: " + e.getMessage());
    }
  }

  private static String docTep(String path) {
    try (InputStream in = new ClassPathResource(path).getInputStream()) {
      // Đọc nguyên văn, không qua bộ phân tích JSON: ruleset_hash cam kết vào đúng byte của
      // tệp, kể cả khoảng trắng và thứ tự khóa.
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new BusinessException("Không đọc được bộ quy tắc " + path + ": " + e.getMessage());
    }
  }
}
