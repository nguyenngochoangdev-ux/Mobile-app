package vn.ptit.drl.audit;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.web3j.crypto.Hash;

import vn.ptit.drl.anchor.Jcs;

/**
 * Công thức mắt xích của chuỗi băm nhật ký — nửa Java.
 * Nửa JS: {@code verifier/src/audit.mjs}.
 *
 * <pre>
 *   hash = keccak256( prevHash(32 byte) ‖ UTF-8( JCS(record) ) )
 * </pre>
 *
 * <p><b>Đây là HỢP ĐỒNG.</b> Ai kiểm toán nhật ký cũng phải tính lại được đúng con số này,
 * nếu không thì cả cơ chế vô nghĩa. <b>Sửa lớp này phải đi kèm {@code /canonical-hash}.</b>
 *
 * <h2>Ba quyết định, và vì sao</h2>
 *
 * <h3>1. {@code prevHash} NULL → 32 byte {@code 0x00}</h3>
 *
 * <p>Bản ghi đầu tiên của cả chuỗi không có bản ghi trước. Cách khác là bỏ hẳn phần
 * {@code prevHash} khỏi tiền ảnh, nhưng như vậy bản ghi đầu tiên dùng một <b>công thức
 * khác</b> với mọi bản ghi sau — hai nhánh mã trong hàm nhạy cảm nhất của cơ chế. Đệm
 * {@code 0x00} giữ đúng một công thức.
 *
 * <h3>2. {@code before}/{@code after} vào tiền ảnh dưới dạng KECCAK CỦA BYTE, không phải JSON</h3>
 *
 * <p>Hai cột đó chứa JSON <b>tuỳ ý</b> do tầng nghiệp vụ sinh ra. Đưa chúng qua {@link Jcs}
 * nghĩa là mọi giá trị nằm ngoài tập con mà {@code Jcs} chấp nhận — số thực ngoài
 * {@code [1e-3, 1e7)}, số nguyên vượt {@code 2^53−1} — sẽ làm <b>ném lỗi giữa lúc ghi nhật
 * ký</b>, tức là một thao tác nghiệp vụ bình thường bị chặn bởi tầng ghi log. Sai hoàn toàn
 * về thứ tự ưu tiên.
 *
 * <p>Băm byte thô thì không có gì để từ chối, mà vẫn cam kết đúng nội dung: đổi một ký tự
 * trong {@code before_json} là đổi {@code beforeHash} là đứt xích.
 *
 * <p>Cái giá: người kiểm toán phải có <b>đúng byte</b> của hai cột đó, không phải "một JSON
 * tương đương". Đó là lý do V7 đổi chúng sang {@code LONGTEXT}.
 *
 * <h3>3. Nội dung cá nhân KHÔNG đi vào payload được neo</h3>
 *
 * <p>{@link AuditPayload} chỉ mang {@code beforeHash}/{@code afterHash}, không mang JSON.
 * Cây Merkle vì thế không bao giờ chạm dữ liệu cá nhân, kể cả gián tiếp — trong khi vẫn
 * chứng minh được nội dung không đổi.
 */
public final class AuditHasher {

  private static final HexFormat HEX = HexFormat.of();

  /** Dùng thay cho {@code prevHash} NULL ở bản ghi đầu tiên. */
  public static final byte[] GENESIS_PREV_HASH = new byte[32];

  private AuditHasher() {}

  /**
   * Cây giá trị được băm để ra mắt xích.
   *
   * <p><b>Không chứa {@code prevHash}</b> — nó được nối vào <i>trước</i> chuỗi JSON dưới dạng
   * 32 byte thô, chứ không nằm trong JSON. Cũng không chứa {@code nonce}: nonce phục vụ lá
   * Merkle ({@link AuditPayload}), không phục vụ mắt xích.
   */
  static Map<String, Object> record(AuditLog e) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("action", e.getAction());
    r.put("actorId", e.getActorId());
    r.put("afterHash", hashOfJson(e.getAfterJson()));
    r.put("at", isoSeconds(e.getCreatedAt()));
    r.put("beforeHash", hashOfJson(e.getBeforeJson()));
    r.put("entity", e.getEntity());
    r.put("entityId", e.getEntityId());
    return r;
  }

  /** Tiền ảnh của mắt xích — tách riêng để test vector đối chiếu từng byte. */
  public static byte[] preimage(byte[] prevHash, Map<String, Object> record) {
    byte[] prev = (prevHash == null || prevHash.length == 0) ? GENESIS_PREV_HASH : prevHash;
    if (prev.length != 32) {
      throw new IllegalArgumentException(
          "prevHash phải dài đúng 32 byte, nhận được: " + prev.length);
    }
    byte[] json = Jcs.canonicalize(record).getBytes(StandardCharsets.UTF_8);

    ByteArrayOutputStream out = new ByteArrayOutputStream(32 + json.length);
    out.writeBytes(prev);
    out.writeBytes(json);
    return out.toByteArray();
  }

  /** Mắt xích của một bản ghi. */
  public static byte[] chainHash(AuditLog e) {
    return Hash.sha3(preimage(e.getPrevHash(), record(e)));
  }

  /**
   * {@code keccak256} của <b>chính byte UTF-8</b> của một chuỗi JSON, hoặc {@code null}.
   *
   * <p>Không phân tích, không canonical hóa — xem javadoc đầu lớp, quyết định 2.
   */
  static String hashOfJson(String json) {
    if (json == null) {
      return null;
    }
    return "0x" + HEX.formatHex(Hash.sha3(json.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * ISO-8601 UTC, độ chính xác GIÂY.
   *
   * <p>Cùng quy tắc với mọi payload khác ({@code docs/canonicalization.md} §4 quy tắc 7).
   * Cột là {@code DATETIME(3)}, nên không cắt thì bản ghi tròn giây và bản ghi có phần lẻ ra
   * hai định dạng khác nhau — và ở đây hậu quả nặng hơn: <b>đứt cả chuỗi</b>.
   */
  static String isoSeconds(Instant t) {
    return t == null ? null : t.truncatedTo(ChronoUnit.SECONDS).toString();
  }
}
