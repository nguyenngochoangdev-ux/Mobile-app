package vn.ptit.drl.scoring;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.web3j.crypto.Hash;

/**
 * Dựng payload miền {@code RULESET}.
 *
 * <p><b>HỢP ĐỒNG giữa backend và verifier.</b> Nửa JS: {@code verifier/src/score.mjs}.
 * <b>Sửa lớp này phải đi kèm {@code /canonical-hash}.</b>
 *
 * <h2>Miền này neo cái gì, và vì sao nó cần tồn tại</h2>
 *
 * <p>Neo điểm mà không neo bộ quy tắc thì <b>sửa quy chế sau khi công bố điểm là việc không
 * ai phát hiện được</b>: con số đã neo vẫn nguyên, nhưng câu chuyện giải thích nó thì đổi.
 * Miền {@code RULESET} đóng cửa đó — {@code ruleset_hash} nằm trong cả payload này lẫn payload
 * {@code SCORE}, nên một điểm số chỉ giải thích được bằng đúng bộ quy tắc đã neo cùng nó.
 *
 * <h2>{@code rulesetHash} băm BYTE THÔ, không qua JCS</h2>
 *
 * <p>Cùng quyết định với {@code AuditHasher.hashOfJson}, cùng lý do: tệp bộ quy tắc là JSON
 * do người soạn quy chế viết, không phải cây giá trị do hệ thống dựng. Đưa nó qua {@code Jcs}
 * là mở đường cho việc một con số nằm ngoài tập con mà {@code Jcs} chấp nhận làm <b>không nạp
 * được bộ quy tắc</b>.
 *
 * <p>Hệ quả có lợi, và là điểm chính: sinh viên tải tệp quy tắc về, băm nguyên văn, và so với
 * giá trị đã neo. <b>Không cần biết JCS là gì.</b> Một lệnh {@code keccak256} trên tệp là đủ.
 *
 * <p>Hệ quả phải chấp nhận: đổi <b>một khoảng trắng</b> trong tệp là đổi hash. Đúng như mong
 * muốn — "cùng nội dung, khác định dạng" vẫn là một tệp khác, và với một văn bản quy chế thì
 * bản đã công bố là bản có hiệu lực, không phải "một bản tương đương về ngữ nghĩa".
 *
 * <h2>Nonce ở đây không phải biện pháp riêng tư</h2>
 *
 * <p>Bộ quy tắc là <b>tài liệu công khai</b> — sinh viên phải đọc được để tự tính lại điểm.
 * Nonce tồn tại để {@link vn.ptit.drl.anchor.LeafHasher} có <b>đúng một</b> đường đi cho cả
 * năm miền; mở ngoại lệ "miền này không cần nonce" là mở nhánh thứ hai trong hàm nhạy cảm
 * nhất của hệ thống. Nonce của ruleset được công bố kèm ruleset. Xem
 * {@code docs/canonicalization.md} §9.2.
 */
public final class RulesetPayload {

  private static final HexFormat HEX = HexFormat.of();

  private RulesetPayload() {}

  /**
   * {@code keccak256} của <b>chính byte UTF-8</b> của tệp bộ quy tắc.
   *
   * <p>Không phân tích, không canonical hóa, không định dạng lại — xem javadoc đầu lớp.
   */
  public static byte[] rulesetHash(String jsonBody) {
    if (jsonBody == null || jsonBody.isBlank()) {
      throw new IllegalArgumentException("Nội dung bộ quy tắc rỗng — không băm được.");
    }
    return Hash.sha3(jsonBody.getBytes(StandardCharsets.UTF_8));
  }

  public static String rulesetHashHex(String jsonBody) {
    return "0x" + HEX.formatHex(rulesetHash(jsonBody));
  }

  /** Dựng payload chuẩn tắc. Năm trường. */
  public static Map<String, Object> of(String version, String semester, byte[] rulesetHash,
                                       Instant effectiveFrom, byte[] nonce) {
    if (rulesetHash == null || rulesetHash.length != 32) {
      throw new IllegalStateException("Bộ quy tắc thiếu rulesetHash 32 byte.");
    }
    if (nonce == null || nonce.length != 16) {
      throw new IllegalStateException(
          "Bộ quy tắc thiếu nonce 16 byte — không neo được. Xem docs/canonicalization.md §9.2.");
    }

    Map<String, Object> p = new LinkedHashMap<>();
    p.put("version", version);
    p.put("semester", semester);
    p.put("rulesetHash", "0x" + HEX.formatHex(rulesetHash));
    p.put("effectiveFrom", ScorePayload.isoSeconds(effectiveFrom));
    p.put("nonce", "0x" + HEX.formatHex(nonce));
    return p;
  }
}
