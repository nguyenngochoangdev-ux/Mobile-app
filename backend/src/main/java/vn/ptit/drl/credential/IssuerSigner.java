package vn.ptit.drl.credential;

import java.math.BigInteger;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Locale;

import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * Ký credential bằng khóa của tổ chức cấp phát, và phục hồi địa chỉ từ chữ ký.
 *
 * <h2>Ký cái gì</h2>
 *
 * <pre>
 *   sig = ECDSA_secp256k1( leaf_hash )        65 byte: r(32) || s(32) || v(1), v ∈ {27, 28}
 * </pre>
 *
 * <p>Ký thẳng <b>leaf hash</b>, không băm lại. Leaf đã là
 * {@code keccak256(bytes8("CRED") ‖ ':' ‖ JCS(payload))} nên nó vừa là thứ đi vào cây Merkle
 * vừa là thông điệp được ký — <b>một digest, hai mục đích</b>. Nhờ vậy không có tầng
 * canonicalization thứ hai nào để lệch: mọi chỗ có thể sai đã nằm trong bộ test vector sẵn có.
 *
 * <h2>Vì sao KHÔNG phải ES256K của JOSE — và tại sao đó là lựa chọn đúng ở đây</h2>
 *
 * <p>ES256K trong JOSE là ECDSA trên secp256k1 với hàm băm SHA-256, chữ ký 64 byte
 * {@code r‖s}, <b>không có recovery id</b>. Muốn kiểm chữ ký đó thì phải biết trước khóa công
 * khai của bên cấp — tức là verifier phải đi tra khóa ở đâu đó. Chỗ duy nhất có nó là máy chủ
 * của trường, mà verifier <b>bị cấm gọi backend một dòng nào</b> (PROJECT.md §4).
 *
 * <p>Byte {@code v} phá được thế bí đó: từ {@code (leaf, sig)} phục hồi thẳng ra <b>địa chỉ
 * ví</b>, rồi hỏi {@code IssuerRegistry} trên chuỗi xem địa chỉ đó có quyền cấp không. Không
 * cần biết trước khóa nào, không cần gọi ai. Đây chính là luận điểm 3 (PROJECT.md §10) —
 * nhiều bên cấp phát, không bên nào độc quyền sổ cái — nên nếu bỏ recovery id thì mất luôn
 * cách hiện thực nó.
 *
 * <p><b>Đánh đổi phải ghi vào phần hạn chế của báo cáo:</b> credential vì thế không phải là
 * JWS/ES256K hợp lệ theo đúng chữ. Nó là chữ ký secp256k1 kiểu Ethereum, thứ mà mọi ví và mọi
 * thư viện EVM kiểm được, nhưng thư viện JOSE thì không. Đổi sang JWS đúng chuẩn là hướng
 * phát triển, và khi đó phải kèm một cách công bố khóa công khai không phụ thuộc máy chủ
 * trường (did:web hoặc chính {@code IssuerRegistry} lưu khóa thay vì địa chỉ).
 *
 * <h2>Ký digest thô có an toàn không</h2>
 *
 * <p>Ký một digest 32 byte tùy ý bằng khóa cũng dùng để gửi giao dịch là mẫu nguy hiểm quen
 * thuộc: kẻ tấn công dụ ký một giá trị hóa ra lại là hash của một giao dịch. Ở đây rủi ro đó
 * bị chặn bằng <b>cấu trúc</b>, không phải bằng may mắn: digest luôn là keccak của một tiền
 * ảnh bắt đầu bằng {@code bytes8("CRED") ‖ ':'} rồi tới JSON chuẩn tắc, nên không có đầu vào
 * nào của bên gọi làm nó trở thành hash RLP của một giao dịch. Đây đúng là mẫu mà EIP-712
 * dùng.
 *
 * <p>Dù vậy vẫn <b>nên dùng khóa khác khóa neo</b> ({@code ANCHOR_PRIVATE_KEY}). Hai khóa hai
 * vai trò: lộ khóa neo thì kẻ tấn công neo được root rác nhưng không cấp được credential giả;
 * lộ khóa issuer thì cấp được credential giả nhưng không neo được. Gộp một khóa là nhân đôi
 * thiệt hại của một lần lộ mà không tiết kiệm được gì. {@link #warnIfSameAsAnchorKey} kiểm
 * điều này lúc khởi động.
 */
public final class IssuerSigner {

  /** r(32) + s(32) + v(1). */
  public static final int SIGNATURE_BYTES = 65;

  private static final int HASH_BYTES = 32;

  private final ECKeyPair keyPair;
  private final String address;

  /**
   * @param privateKeyHex khóa riêng 32 byte, có hoặc không có tiền tố {@code 0x}
   */
  public IssuerSigner(String privateKeyHex) {
    if (privateKeyHex == null || privateKeyHex.isBlank()) {
      throw new IllegalArgumentException(
          "Thiếu khóa riêng của tổ chức cấp phát (ISSUER_PRIVATE_KEY).");
    }
    this.keyPair = ECKeyPair.create(Numeric.toBigInt(privateKeyHex.trim()));
    this.address = "0x" + Keys.getAddress(keyPair).toLowerCase(Locale.ROOT);
  }

  /**
   * Địa chỉ ví của bên cấp phát — {@code 0x} + 40 hex <b>chữ thường</b>.
   *
   * <p>Chữ thường chứ không phải dạng checksum EIP-55: giá trị này đi thẳng vào payload được
   * neo, và EIP-55 trộn hoa/thường theo hash của chính địa chỉ nên hai phía chuẩn hóa khác
   * nhau là ra hai chuỗi JCS khác nhau. Xem {@link CredentialPayload}.
   */
  public String address() {
    return address;
  }

  /**
   * Ký một leaf hash.
   *
   * @param leafHash đúng 32 byte
   * @return 65 byte {@code r‖s‖v}
   */
  public byte[] sign(byte[] leafHash) {
    requireHash(leafHash);

    // needToHash = false: leaf ĐÃ là keccak256. Để true thì web3j băm thêm một vòng kèm tiền
    // tố EIP-191, và phía verifier gọi recoverAddress trên digest thô sẽ ra một địa chỉ khác
    // hoàn toàn — im lặng, vì phục hồi luôn trả về MỘT địa chỉ nào đó chứ không báo lỗi.
    Sign.SignatureData sig = Sign.signMessage(leafHash, keyPair, false);

    byte[] out = new byte[SIGNATURE_BYTES];
    System.arraycopy(sig.getR(), 0, out, 0, 32);
    System.arraycopy(sig.getS(), 0, out, 32, 32);
    out[64] = sig.getV()[0];
    return out;
  }

  // ------------------------------------------------------------------ tĩnh

  /**
   * Phục hồi địa chỉ ví đã ký — <b>nửa Java của thứ verifier làm bằng
   * {@code ethers.recoverAddress}</b>.
   *
   * <p>Lưu ý về ngữ nghĩa: hàm này <b>luôn</b> trả về một địa chỉ nào đó với bất kỳ chữ ký
   * đúng định dạng nào. Nó không phải phép kiểm "chữ ký có hợp lệ không". Phép kiểm thật là:
   * địa chỉ phục hồi được có <b>khớp</b> {@code issuerAddress} trong payload không, và địa chỉ
   * đó có nằm trong {@code IssuerRegistry} không. Bên gọi phải làm cả hai.
   *
   * @return {@code 0x} + 40 hex chữ thường
   */
  public static String recoverAddress(byte[] leafHash, byte[] signature) {
    requireHash(leafHash);
    if (signature == null || signature.length != SIGNATURE_BYTES) {
      throw new IllegalArgumentException(
          "Chữ ký phải dài đúng " + SIGNATURE_BYTES + " byte (r‖s‖v), nhận được: "
              + (signature == null ? "null" : signature.length + " byte"));
    }

    byte v = signature[64];
    if (v != 27 && v != 28) {
      throw new IllegalArgumentException(
          "Byte v phải là 27 hoặc 28, nhận được: " + (v & 0xFF)
              + ". Chữ ký kiểu EIP-155 (v gắn chainId) không dùng ở đây.");
    }

    Sign.SignatureData sig = new Sign.SignatureData(
        v,
        Arrays.copyOfRange(signature, 0, 32),
        Arrays.copyOfRange(signature, 32, 64));

    BigInteger publicKey;
    try {
      publicKey = Sign.signedMessageHashToKey(leafHash, sig);
    } catch (SignatureException e) {
      throw new IllegalArgumentException("Không phục hồi được khóa công khai từ chữ ký.", e);
    }
    return "0x" + Keys.getAddress(publicKey).toLowerCase(Locale.ROOT);
  }

  /**
   * Cảnh báo khi khóa issuer trùng khóa neo. Không ném lỗi — hệ vẫn chạy đúng, chỉ là gộp hai
   * vai trò vào một khóa nên một lần lộ gây thiệt hại gấp đôi. Xem javadoc đầu lớp.
   */
  public static boolean isSameKey(String issuerPrivateKey, String anchorPrivateKey) {
    // Rỗng chứ không chỉ null: ANCHOR_PRIVATE_KEY trong `.env` là chuỗi rỗng chừng nào chuỗi
    // còn tắt, và Numeric.toBigInt("") ném "Zero length BigInteger". Không có nhánh này thì
    // ứng dụng KHÔNG KHỞI ĐỘNG ĐƯỢC ở cấu hình mặc định — vừa cấu hình khóa issuer là sập,
    // với một thông báo lỗi chẳng liên quan gì tới nguyên nhân.
    if (isBlank(issuerPrivateKey) || isBlank(anchorPrivateKey)) {
      return false;
    }
    try {
      return Numeric.toBigInt(issuerPrivateKey.trim())
          .equals(Numeric.toBigInt(anchorPrivateKey.trim()));
    } catch (RuntimeException e) {
      // Một trong hai khóa không phải hex hợp lệ. Đây là phép kiểm CẢNH BÁO, không phải phép
      // kiểm tính đúng đắn — nó không được quyền làm sập ứng dụng. Khóa issuer hỏng thật thì
      // constructor đã ném rồi; khóa neo hỏng thật thì AnchorChainConfig sẽ ném.
      return false;
    }
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  /** Chỗ gọi {@link #isSameKey} lúc khởi động — xem {@code CredentialConfig}. */
  static void warnIfSameAsAnchorKey(String issuerPrivateKey, String anchorPrivateKey,
                                    org.slf4j.Logger log) {
    if (isSameKey(issuerPrivateKey, anchorPrivateKey)) {
      log.warn("ISSUER_PRIVATE_KEY trung ANCHOR_PRIVATE_KEY. He van chay dung, nhung mot lan"
          + " lo khoa se vua cap duoc credential gia vua neo duoc root rac. Tach hai khoa"
          + " truoc buoi nghiem thu.");
    }
  }

  private static void requireHash(byte[] h) {
    if (h == null || h.length != HASH_BYTES) {
      throw new IllegalArgumentException(
          "Leaf hash phải dài đúng " + HASH_BYTES + " byte, nhận được: "
              + (h == null ? "null" : h.length + " byte"));
    }
  }
}
