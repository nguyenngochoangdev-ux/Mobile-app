package vn.ptit.drl.anchor;

import java.nio.charset.StandardCharsets;

/**
 * Năm miền neo. KHÔNG tự thêm giá trị thứ sáu — mỗi miền là một cây Merkle riêng và
 * một dòng riêng trong {@code AnchorRegistry}. Thêm miền là đổi lược đồ on-chain,
 * phải qua {@code /scope-guard}.
 *
 * <p>Bản sao phía JS: {@code DOMAINS} trong {@code verifier/src/leaf.mjs}.
 */
public enum AnchorDomain {
  ATTEND,
  CRED,
  SCORE,
  AUDIT,
  RULESET;

  /** Độ dài kiểu {@code bytes8} của Solidity. Tên dài nhất là RULESET (7 ký tự). */
  public static final int WIDTH = 8;

  /**
   * Mã hóa tên miền thành 8 byte, <b>căn trái, đệm {@code 0x00} bên phải</b> — khớp
   * đúng kiểu {@code bytes8} trong Solidity.
   *
   * <p>Cùng một giá trị byte được dùng cả trong tiền ảnh của leaf hash lẫn trong
   * tham số gọi contract, nên không có chỗ nào để lệch giữa ba tầng (Java, JS,
   * Solidity).
   */
  public byte[] toBytes8() {
    byte[] out = new byte[WIDTH]; // Java khởi tạo sẵn 0x00
    byte[] ascii = name().getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(ascii, 0, out, 0, ascii.length);
    return out;
  }

  /** Phân giải tên miền, ném lỗi rõ ràng thay vì {@code IllegalArgumentException} trống. */
  public static AnchorDomain of(String name) {
    for (AnchorDomain d : values()) {
      if (d.name().equals(name)) return d;
    }
    throw new IllegalArgumentException(
        "Miền neo không hợp lệ: \"" + name + "\". Chỉ có: ATTEND, CRED, SCORE, AUDIT, RULESET");
  }
}
