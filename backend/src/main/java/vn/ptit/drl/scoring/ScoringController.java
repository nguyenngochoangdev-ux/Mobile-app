package vn.ptit.drl.scoring;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import vn.ptit.drl.common.web.NotFoundException;
import vn.ptit.drl.identity.jwt.AuthPrincipal;

@RestController
@RequestMapping("/api/scoring")
@RequiredArgsConstructor
@Tag(name = "Scoring", description = "Chấm điểm rèn luyện theo bộ quy tắc có version")
public class ScoringController {

  private static final HexFormat HEX = HexFormat.of();

  private final ScoringService service;
  private final ScoreRepository scores;

  /**
   * Điểm của một sinh viên.
   *
   * <p>Mang theo {@code evidenceHash} và {@code rulesetHash} — không phải để hiển thị đẹp, mà
   * vì đó là <b>hai thứ làm cho điểm này kiểm lại được</b>. Giao diện in chúng ra để sinh viên
   * đối chiếu với giá trị đã neo; giấu đi thì điểm lại trở về một con số phải tin.
   */
  public record ScoreResponse(Long id, Long runId, String semester, String rulesetVersion,
                              String rulesetHash, Instant scoredAt,
                              Integer c1, Integer c2, Integer c3, Integer c4, Integer c5,
                              Integer total, String classification,
                              String evidenceHash, boolean daNeo) {

    static ScoreResponse of(Score s) {
      Ruleset r = s.getRun().getRuleset();
      return new ScoreResponse(
          s.getId(), s.getRun().getId(), s.getRun().getSemester(), r.getVersion(),
          "0x" + HEX.formatHex(r.getRulesetHash()), s.getRun().getRunAt(),
          s.getC1(), s.getC2(), s.getC3(), s.getC4(), s.getC5(),
          s.getTotal(), s.getClassification(),
          s.getEvidenceHash() == null ? null : "0x" + HEX.formatHex(s.getEvidenceHash()),
          s.getLeafHash() != null);
    }
  }

  @GetMapping("/me")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(operationId = "myScores", summary = "Điểm rèn luyện của chính mình, mới trước cũ sau")
  @Transactional(readOnly = true)
  public List<ScoreResponse> mine(@AuthenticationPrincipal AuthPrincipal principal) {
    return scores.findByStudentNewestFirst(principal.studentId(), PageRequest.of(0, 20))
        .stream().map(ScoreResponse::of).toList();
  }

  @GetMapping("/student/{studentId}")
  @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
  @Operation(operationId = "scoresOfStudent", summary = "Điểm của một sinh viên")
  @Transactional(readOnly = true)
  public List<ScoreResponse> ofStudent(@PathVariable Long studentId) {
    return scores.findByStudentNewestFirst(studentId, PageRequest.of(0, 20))
        .stream().map(ScoreResponse::of).toList();
  }

  // ---------------------------------------------------------------- chạy chấm

  public record RunRequest(
      @NotBlank @Pattern(regexp = "^\\d{4}-[12]$",
          message = "Học kỳ phải có dạng YYYY-1 hoặc YYYY-2")
      String semester,
      @NotBlank String rulesetVersion) {}

  @PostMapping("/run")
  @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
  @Operation(operationId = "runScoring", summary = "Chấm điểm toàn khóa cho một học kỳ",
      description = """
          Nạp bộ quy tắc từ `classpath:rulesets/<version>.json`, KIỂM nó, rồi mới chấm — có
          lỗi thì không chấm ai cả.

          Chấm lại tạo LƯỢT MỚI, không sửa lượt cũ: điểm đã chấm là phát biểu đã (sắp) được
          neo, sửa nó làm mọi Merkle proof fail vĩnh viễn.

          Mỗi bản ghi mang `evidence_hash` — cam kết vào đúng tập bản ghi điểm danh đã dùng.
          Cùng với `ruleset_hash`, nó làm điểm số tái tính lại được bởi người ngoài mà không
          cần máy chủ của trường.
          """)
  @Transactional
  public ScoringService.KetQuaLuot run(@Valid @RequestBody RunRequest req,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
    return service.chamHocKy(req.semester(), req.rulesetVersion(), principal.userId());
  }

  // ---------------------------------------------------------------- bộ quy tắc

  /**
   * Bộ quy tắc — <b>trả về nguyên văn</b> nội dung tệp.
   *
   * @param jsonBody đúng byte đã băm thành {@code rulesetHash}. Sinh viên tải về, băm bằng
   *     một lệnh {@code keccak256}, và so với giá trị đã neo — <b>không cần biết JCS là gì</b>.
   *     In lại cho đẹp là làm hỏng chính tính chất đó.
   */
  public record RulesetResponse(Long id, String version, String semester, String rulesetHash,
                                Instant effectiveFrom, boolean daNeo,
                                int diemTuDuLieu, int diemMacDinh, String jsonBody) {}

  @GetMapping("/rulesets/{semester}")
  @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
  @Operation(operationId = "getRuleset", summary = "Bộ quy tắc đang áp dụng cho một học kỳ",
      description = """
          Trả về NGUYÊN VĂN nội dung tệp. Đây là tài liệu công khai — sinh viên phải đọc được
          để tự tính lại điểm của mình.

          `diemTuDuLieu` / `diemMacDinh` nói bao nhiêu phần của thang 100 là kết quả đo đạc và
          bao nhiêu là giả định. Hai con số này tính từ chính bộ quy tắc, nên chúng không lệch
          được khỏi tệp đang dùng.
          """)
  @Transactional(readOnly = true)
  public RulesetResponse ruleset(@PathVariable String semester) {
    Ruleset r = service.rulesetCuaHocKy(semester)
        .orElseThrow(() -> new NotFoundException(
            "Chưa có bộ quy tắc nào cho học kỳ " + semester));

    RulesetDoc doc = service.doc(r);
    return new RulesetResponse(
        r.getId(), r.getVersion(), r.getSemester(),
        "0x" + HEX.formatHex(r.getRulesetHash()), r.getEffectiveFrom(),
        r.getLeafHash() != null, doc.diemTuDuLieu(), doc.diemMacDinh(), r.getJsonBody());
  }
}
