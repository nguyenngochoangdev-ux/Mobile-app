package vn.ptit.drl.anchor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * JCS — JSON Canonicalization Scheme (RFC 8785), tập con giới hạn.
 *
 * <p>Đây là NỬA JAVA của cạm bẫy số 2 trong CLAUDE.md. Nửa còn lại là
 * {@code verifier/src/jcs.mjs}. Hai file phải sinh ra BYTE GIỐNG HỆT NHAU.
 * Sửa một bên mà không sửa bên kia = mọi Merkle proof fail, và fail im lặng.
 *
 * <p>Đặc tả chốt: {@code docs/canonicalization.md}. Đọc trước khi sửa.
 *
 * <p><b>Cố ý KHÔNG dùng Jackson.</b> Jackson tuần tự hóa theo cấu hình
 * ({@code @JsonInclude}, {@code @JsonProperty}, module đăng ký, thứ tự field của
 * POJO) — nghĩa là leaf hash sẽ phụ thuộc vào những thứ có thể đổi mà không ai để
 * ý, kể cả một lần nâng version. Lớp này nhận cây giá trị tường minh
 * ({@link Map}/{@link List}/{@link String}/{@link Boolean}/{@link Number}/null)
 * và không có cấu hình nào. Nhờ vậy tầng canonicalization miễn nhiễm với việc nâng
 * Jackson 2 → 3 nêu ở PROJECT.md §2.1.
 *
 * <p>Lớp này thuộc module {@code anchor} nên KHÔNG import gì từ nghiệp vụ
 * (PROJECT.md §5).
 */
public final class Jcs {

  /** Giới hạn số nguyên an toàn của JS: 2^53 − 1. Vượt ngưỡng là JS mất chính xác. */
  public static final long MAX_SAFE_INTEGER = 9007199254740991L;

  /**
   * Vùng an toàn cho số thực — xem {@code docs/canonicalization.md} §4.
   *
   * <p>{@code Double.toString} của Java chuyển sang ký hiệu mũ khi |v| ≥ 1e7 hoặc
   * &lt; 1e-3; {@code Number.prototype.toString} của JS chuyển khi |v| ≥ 1e21 hoặc
   * &lt; 1e-6. Vùng giao nhau — nơi hai ngôn ngữ CHẮC CHẮN cho ra cùng chuỗi — là
   * [1e-3, 1e7). Ngoài vùng này ta NÉM LỖI thay vì đoán: thà vỡ ồn ào lúc chạy còn
   * hơn lệch hash im lặng ở tuần 6.
   */
  private static final double MIN_ABS_FRACTIONAL = 1e-3;

  private static final double MAX_ABS_FRACTIONAL = 1e7;

  private Jcs() {}

  /** Trả về chuỗi JSON chuẩn tắc của {@code value}. */
  public static String canonicalize(Object value) {
    StringBuilder sb = new StringBuilder();
    write(value, sb);
    return sb.toString();
  }

  private static void write(Object v, StringBuilder out) {
    switch (v) {
      case null -> out.append("null");
      case Boolean b -> out.append(b ? "true" : "false");
      case String s -> writeString(s, out);
      case Number n -> out.append(number(n));
      case Map<?, ?> m -> writeObject(m, out);
      case List<?> l -> writeArray(l, out);
      default ->
          throw new IllegalArgumentException(
              "JCS: kiểu không hỗ trợ (" + v.getClass().getName() + ") — không neo được");
    }
  }

  /**
   * Khóa sắp xếp theo thứ tự <b>đơn vị mã UTF-16</b> ở mọi cấp lồng nhau.
   *
   * <p>{@link String#compareTo} của Java so sánh theo đơn vị mã UTF-16, và
   * {@code Array.prototype.sort()} mặc định của JS cũng vậy — nên hai phía khớp
   * nhau tự nhiên, kể cả với ký tự ngoài BMP. Đây là điều RFC 8785 §3.2.3 yêu cầu.
   */
  private static void writeObject(Map<?, ?> m, StringBuilder out) {
    TreeMap<String, Object> sorted = new TreeMap<>();
    for (Map.Entry<?, ?> e : m.entrySet()) {
      if (!(e.getKey() instanceof String k)) {
        throw new IllegalArgumentException("JCS: khóa object phải là chuỗi — không neo được");
      }
      sorted.put(k, e.getValue());
    }
    out.append('{');
    boolean first = true;
    for (Map.Entry<String, Object> e : sorted.entrySet()) {
      if (!first) out.append(',');
      first = false;
      writeString(e.getKey(), out);
      out.append(':');
      write(e.getValue(), out);
    }
    out.append('}');
  }

  /** Mảng GIỮ NGUYÊN thứ tự phần tử — chỉ khóa của object mới được sắp xếp. */
  private static void writeArray(List<?> l, StringBuilder out) {
    out.append('[');
    for (int i = 0; i < l.size(); i++) {
      if (i > 0) out.append(',');
      write(l.get(i), out);
    }
    out.append(']');
  }

  /** Thoát chuỗi theo RFC 8785 §3.2.2.2 — giống hệt {@code JSON.stringify} của ES. */
  private static void writeString(String s, StringBuilder out) {
    out.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\t' -> out.append("\\t");
        case '\n' -> out.append("\\n");
        case '\f' -> out.append("\\f");
        case '\r' -> out.append("\\r");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else if (Character.isSurrogate(c)) {
            // Cặp thay thế hợp lệ thì giữ nguyên cả hai đơn vị mã (emoji, chữ hiếm).
            boolean paired =
                Character.isHighSurrogate(c)
                    && i + 1 < s.length()
                    && Character.isLowSurrogate(s.charAt(i + 1));
            if (!paired) {
              // Surrogate lẻ: JSON.stringify của ES2019 escape nó thành một chuỗi
              // thoát 6 ký tự, Java thì không. Không thống nhất được → cấm.
              // Dữ liệu thật không bao giờ có ký tự này.
              // (Đừng viết chuỗi thoát u-hex trong comment: Java diễn giải nó ngay
              //  cả trong comment, và file sẽ không biên dịch được.)
              throw new IllegalArgumentException(
                  "JCS: chuỗi chứa surrogate lẻ tại vị trí " + i + " — không neo được");
            }
            out.append(c).append(s.charAt(i + 1));
            i++;
          } else {
            // Tiếng Việt có dấu đi thẳng ra UTF-8 thô, KHÔNG dùng chuỗi thoát u-hex.
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }

  /**
   * Tuần tự hóa số theo tập con an toàn (xem {@link #MIN_ABS_FRACTIONAL}).
   *
   * <p>Bẫy kinh điển nằm ở đây: {@code Double.toString(20.0)} cho {@code "20.0"}
   * còn {@code String(20.0)} của JS cho {@code "20"}. Nhánh "số thực nguyên vẹn"
   * bên dưới là chỗ xử lý.
   */
  static String number(Number n) {
    if (n instanceof Byte || n instanceof Short || n instanceof Integer || n instanceof Long) {
      return integer(BigInteger.valueOf(n.longValue()));
    }
    if (n instanceof BigInteger bi) {
      return integer(bi);
    }
    double v = n instanceof BigDecimal bd ? bd.doubleValue() : n.doubleValue();
    if (Double.isNaN(v) || Double.isInfinite(v)) {
      throw new IllegalArgumentException("JCS: số không hữu hạn (" + n + ") — không neo được");
    }
    if (v == 0.0d) {
      return "0"; // gộp cả -0.0 về "0", theo RFC 8785
    }
    if (v == Math.rint(v)) { // số thực nhưng nguyên vẹn: 20.0 → "20", không phải "20.0"
      if (Math.abs(v) > MAX_SAFE_INTEGER) {
        throw new IllegalArgumentException(
            "JCS: số nguyên " + n + " vượt MAX_SAFE_INTEGER — không neo được");
      }
      return Long.toString((long) v);
    }
    double abs = Math.abs(v);
    if (abs < MIN_ABS_FRACTIONAL || abs >= MAX_ABS_FRACTIONAL) {
      throw new IllegalArgumentException(
          "JCS: số thực "
              + n
              + " nằm ngoài vùng an toàn [1e-3, 1e7) — Java và JS sẽ sinh chuỗi khác"
              + " nhau. Xem docs/canonicalization.md §4.");
    }
    String s = Double.toString(v);
    if (s.indexOf('E') >= 0 || s.indexOf('e') >= 0) {
      // Chốt chặn cuối. Nếu bao giờ chạm vào đây nghĩa là ngưỡng ở trên sai,
      // KHÔNG phải là lúc nới ngưỡng ra cho qua.
      throw new IllegalArgumentException(
          "JCS: Double.toString(" + n + ") sinh ký hiệu mũ \"" + s + "\" — không neo được");
    }
    return s;
  }

  private static String integer(BigInteger bi) {
    if (bi.abs().compareTo(BigInteger.valueOf(MAX_SAFE_INTEGER)) > 0) {
      throw new IllegalArgumentException(
          "JCS: số nguyên " + bi + " vượt MAX_SAFE_INTEGER — không neo được");
    }
    return bi.toString();
  }
}
