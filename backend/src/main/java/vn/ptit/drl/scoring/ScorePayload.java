package vn.ptit.drl.scoring;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dựng payload miền {@code SCORE}.
 *
 * <p><b>HỢP ĐỒNG giữa backend và verifier.</b> Nửa JS: {@code verifier/src/score.mjs}.
 * Vector: {@code canonical-vectors.json}, tiền tố {@code score-payload}.
 * <b>Sửa lớp này phải đi kèm {@code /canonical-hash}.</b>
 *
 * <h2>Mười bốn trường, và hai trong số đó làm nên cả ý nghĩa</h2>
 *
 * <p>Mười hai trường còn lại chỉ nói <i>điểm là bao nhiêu</i>. Hai trường này nói <i>vì sao
 * nó là con số đó</i>:
 *
 * <table>
 *   <tr><th>Trường</th><th>Trả lời câu hỏi</th></tr>
 *   <tr><td>{@code evidenceHash}</td><td><b>Chấm trên dữ liệu nào?</b> — cam kết vào đúng tập
 *       bản ghi điểm danh đã dùng, qua leaf hash của chúng ở miền {@code ATTEND}</td></tr>
 *   <tr><td>{@code rulesetHash}</td><td><b>Chấm bằng quy tắc nào?</b> — cam kết vào đúng byte
 *       của tệp bộ quy tắc, đã neo riêng ở miền {@code RULESET}</td></tr>
 * </table>
 *
 * <p>Có cả hai thì một điểm số <b>tái tính lại được bởi người ngoài</b> mà không cần máy chủ
 * của trường. Thiếu một trong hai thì "chấm tự động" chỉ là chuyển việc tin cán bộ sang tin
 * máy chủ. Xem {@link EvidenceHasher} cho lập luận đầy đủ.
 *
 * <h2>Vì sao có {@code scoredAt}</h2>
 *
 * <p>Chấm lại cùng một học kỳ tạo ra bản ghi điểm mới. Nếu payload không mang mốc thời gian
 * thì hai lần chấm cho ra hai leaf gần như giống hệt (chỉ khác {@code nonce}), và người xác
 * minh <b>không phân biệt được bản nào là bản sau</b>. Một trường ISO-8601 rẻ hơn nhiều so
 * với một cuộc tranh cãi về việc điểm nào mới đúng.
 *
 * <h2>Vì sao KHÔNG có id bản ghi</h2>
 *
 * <p>{@code scores.id} và {@code run_id} chỉ có nghĩa bên trong CSDL của trường. Payload này
 * là một <b>phát biểu</b> — "sinh viên X, học kỳ Y, theo bộ quy tắc Z, trên bằng chứng W,
 * được N điểm" — và phát biểu đó phải đứng vững khi CSDL không còn.
 */
public final class ScorePayload {

  private static final HexFormat HEX = HexFormat.of();

  private ScorePayload() {}

  /**
   * Dựng payload chuẩn tắc.
   *
   * @param nonce 16 byte từ {@code scores.nonce}
   */
  public static Map<String, Object> of(String studentCode, String semester,
                                       String rulesetVersion, byte[] rulesetHash,
                                       Instant scoredAt, Map<String, Integer> diemTheoTieuChi,
                                       int total, String classification,
                                       byte[] evidenceHash, byte[] nonce) {

    require32(rulesetHash, "rulesetHash");
    require32(evidenceHash, "evidenceHash");
    if (nonce == null || nonce.length != 16) {
      throw new IllegalStateException(
          "Bản ghi điểm thiếu nonce 16 byte — không neo được. Xem PROJECT.md §2.3.");
    }

    Map<String, Object> p = new LinkedHashMap<>();
    p.put("studentCode", studentCode);
    p.put("semester", semester);
    p.put("rulesetVersion", rulesetVersion);
    p.put("rulesetHash", "0x" + HEX.formatHex(rulesetHash));
    p.put("scoredAt", isoSeconds(scoredAt));
    // Năm tiêu chí ghi phẳng chứ không lồng trong một object `criteria`: chúng là cột thật
    // trong bảng `scores` và số lượng của chúng do Thông tư 16/2015 chốt, không phải thứ
    // thay đổi theo cấu hình. Lồng lại chỉ thêm một tầng mà không thêm gì.
    p.put("c1", diemTheoTieuChi.getOrDefault("C1", 0));
    p.put("c2", diemTheoTieuChi.getOrDefault("C2", 0));
    p.put("c3", diemTheoTieuChi.getOrDefault("C3", 0));
    p.put("c4", diemTheoTieuChi.getOrDefault("C4", 0));
    p.put("c5", diemTheoTieuChi.getOrDefault("C5", 0));
    p.put("total", total);
    p.put("classification", classification);
    p.put("evidenceHash", "0x" + HEX.formatHex(evidenceHash));
    p.put("nonce", "0x" + HEX.formatHex(nonce));
    return p;
  }

  private static void require32(byte[] h, String ten) {
    if (h == null || h.length != 32) {
      throw new IllegalStateException(
          "Bản ghi điểm thiếu " + ten + " 32 byte — không neo được."
              + " Điểm không có bằng chứng đầu vào thì việc neo nó vô nghĩa.");
    }
  }

  /** ISO-8601 UTC, độ chính xác GIÂY — {@code docs/canonicalization.md} §4 quy tắc 7. */
  static String isoSeconds(Instant t) {
    return t == null ? null : t.truncatedTo(ChronoUnit.SECONDS).toString();
  }
}
