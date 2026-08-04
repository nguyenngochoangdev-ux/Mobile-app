package vn.ptit.drl.anchor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;
import org.web3j.crypto.Hash;

/**
 * Công thức leaf hash — nửa Java. Nửa JS: {@code verifier/src/leaf.mjs}.
 *
 * <pre>
 *   leaf = keccak256( bytes8(domain) || 0x3A || UTF-8(JCS(payload)) )
 * </pre>
 *
 * <p>{@code 0x3A} là dấu {@code ':'} trong ASCII, giữ đúng công thức PROJECT.md §2.3.
 * Đặc tả đầy đủ: {@code docs/canonicalization.md}.
 *
 * <p>Module {@code anchor} không biết gì về nghiệp vụ (PROJECT.md §5): lớp này nhận
 * {@link Map} và trả byte, không import class nào từ {@code attendance} hay
 * {@code scoring}.
 */
public final class LeafHasher {

  private static final byte SEPARATOR = 0x3A; // ':'

  /** 16 byte, theo PROJECT.md §2.3. */
  public static final int NONCE_BYTES = 16;

  private static final Pattern NONCE_PATTERN = Pattern.compile("^0x[0-9a-f]{32}$");
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final HexFormat HEX = HexFormat.of(); // mặc định chữ thường

  private LeafHasher() {}

  /**
   * Sinh nonce mới. Lưu cùng bản ghi trong DB (cột {@code nonce BINARY(16)}) — mất
   * nonce là mất khả năng tự xác minh bản ghi đó.
   */
  public static String newNonce() {
    byte[] b = new byte[NONCE_BYTES];
    RANDOM.nextBytes(b);
    return "0x" + HEX.formatHex(b);
  }

  /**
   * Kiểm tra {@code nonce} trước khi hash.
   *
   * <p>Đây là biện pháp cấu trúc cho lỗ hổng ở PROJECT.md §2.3: không có nonce thì
   * payload nằm trong không gian đoán được (MSSV vài chục nghìn × eventId vài trăm ×
   * thời gian trong một buổi ≈ 10^8–10^9 tổ hợp), và ai cầm một leaf hash đều vét cạn
   * khôi phục được nội dung trong vài giây. Nó vỡ ngay khi sinh viên xuất bundle, vì
   * proof chứa hash bản ghi CỦA SINH VIÊN KHÁC.
   *
   * <p>Bắt buộc ở đây thay vì "nhớ thêm vào" khiến không thể quên.
   */
  private static void requireNonce(Map<String, Object> payload) {
    Object n = payload == null ? null : payload.get("nonce");
    if (!(n instanceof String s) || !NONCE_PATTERN.matcher(s).matches()) {
      throw new IllegalArgumentException(
          "Payload thiếu `nonce` hợp lệ. Yêu cầu: chuỗi hex 16 byte, chữ thường, tiền tố"
              + " \"0x\" (34 ký tự). Xem PROJECT.md §2.3.");
    }
  }

  /** Tiền ảnh của leaf hash — tách riêng để test vector đối chiếu được từng byte. */
  public static byte[] preimage(AnchorDomain domain, Map<String, Object> payload) {
    requireNonce(payload);
    byte[] json = Jcs.canonicalize(payload).getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream out = new ByteArrayOutputStream(AnchorDomain.WIDTH + 1 + json.length);
    out.writeBytes(domain.toBytes8());
    out.write(SEPARATOR);
    out.writeBytes(json);
    return out.toByteArray();
  }

  /** Leaf hash dạng 32 byte thô — dùng để dựng cây Merkle. */
  public static byte[] leaf(AnchorDomain domain, Map<String, Object> payload) {
    // web3j đặt tên hàm là sha3 vì lý do lịch sử; nó là Keccak-256, đúng thứ EVM dùng,
    // KHÔNG phải SHA3-256 của NIST (hai thuật toán khác nhau ở phần đệm).
    return Hash.sha3(preimage(domain, payload));
  }

  /** Leaf hash dạng chuỗi hex {@code 0x…} — dùng để log, so sánh, và test vector. */
  public static String leafHex(AnchorDomain domain, Map<String, Object> payload) {
    return "0x" + HEX.formatHex(leaf(domain, payload));
  }
}
