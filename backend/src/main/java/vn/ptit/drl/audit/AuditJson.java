package vn.ptit.drl.audit;

import java.util.LinkedHashMap;
import java.util.Map;

import vn.ptit.drl.anchor.Jcs;

/**
 * Dựng chuỗi JSON cho {@code before_json} / {@code after_json}.
 *
 * <h2>Vì sao có lớp này thay vì nối chuỗi tại chỗ</h2>
 *
 * <p>Nối thẳng giá trị người dùng nhập vào JSON là lỗi kinh điển: một dấu nháy kép trong lý do
 * thu hồi làm hỏng cả bản ghi, và bản ghi hỏng thì <b>mắt xích của nó vô nghĩa</b>. Ở nơi khác
 * lỗi đó chỉ làm xấu một dòng log; ở đây nó phá bằng chứng.
 *
 * <p>Dùng {@link Jcs} chứ không phải Jackson, vì hai lý do độc lập:
 *
 * <ul>
 *   <li><b>Tất định.</b> Byte của chuỗi này đi thẳng vào {@code beforeHash}/{@code afterHash}
 *       ({@link AuditHasher}). Jackson tuần tự hóa theo cấu hình — {@code @JsonInclude}, module
 *       đăng ký, thứ tự field — nên cùng một dữ liệu có thể ra hai chuỗi khác nhau sau một lần
 *       nâng version, và khi đó nhật ký cũ không kiểm lại được.
 *   <li><b>Không thêm phụ thuộc mới</b> vào đường đi của bằng chứng.
 * </ul>
 *
 * <p>{@code Jcs} từ chối vài loại giá trị (số thực ngoài {@code [1e-3, 1e7)}, số nguyên vượt
 * {@code 2^53−1}). Ở đây điều đó <b>an toàn</b> vì bên gọi tự dựng map từ các giá trị mình
 * kiểm soát — khác hẳn với {@code before_json} do tầng nghiệp vụ đưa vào nguyên khối, thứ mà
 * {@link AuditHasher} cố ý băm byte thô để không bao giờ từ chối.
 */
public final class AuditJson {

  private AuditJson() {}

  /**
   * Chuỗi JSON chuẩn tắc từ các cặp khóa–giá trị.
   *
   * <pre>
   *   AuditJson.of("status", "PENDING", "deviceId", 12L)
   *   // {"deviceId":12,"status":"PENDING"}
   * </pre>
   *
   * @param keyValues số phần tử phải chẵn; khóa là {@code String}
   */
  public static String of(Object... keyValues) {
    if (keyValues.length % 2 != 0) {
      throw new IllegalArgumentException(
          "Cần số phần tử chẵn (khóa, giá trị), nhận được: " + keyValues.length);
    }
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      if (!(keyValues[i] instanceof String k)) {
        throw new IllegalArgumentException(
            "Khóa phải là chuỗi, phần tử " + i + " là: " + keyValues[i]);
      }
      m.put(k, keyValues[i + 1]);
    }
    return Jcs.canonicalize(m);
  }
}
