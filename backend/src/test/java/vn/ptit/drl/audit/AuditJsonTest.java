package vn.ptit.drl.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AuditJson} — lớp nhỏ nhưng nằm trên đường đi của bằng chứng.
 *
 * <p>Byte của chuỗi nó sinh ra đi thẳng vào {@code beforeHash}/{@code afterHash}, nên hai
 * tính chất phải đúng: <b>tất định</b> (cùng dữ liệu ra cùng byte, mãi mãi) và <b>thoát ký tự
 * đúng</b> (một dấu nháy kép trong lý do thu hồi không được làm hỏng bản ghi).
 */
class AuditJsonTest {

  @Test
  @DisplayName("Khóa được sắp xếp — thứ tự truyền vào không ảnh hưởng byte")
  void tatDinhTheoNoiDung() {
    String a = AuditJson.of("status", "ACTIVE", "deviceId", 12L);
    String b = AuditJson.of("deviceId", 12L, "status", "ACTIVE");

    assertEquals(a, b,
        "Cùng dữ liệu phải ra cùng byte bất kể thứ tự truyền vào — nếu không, hai chỗ gọi"
            + " khác nhau sẽ sinh hai hash khác nhau cho cùng một sự kiện.");
    assertEquals("{\"deviceId\":12,\"status\":\"ACTIVE\"}", a);
  }

  @Test
  @DisplayName("Số nguyên không có phần thập phân — bẫy Java↔JS kinh điển")
  void soNguyen() {
    assertEquals("{\"n\":12}", AuditJson.of("n", 12L));
    assertEquals("{\"n\":0}", AuditJson.of("n", 0L));
  }

  @Test
  @DisplayName("null giữ nguyên, không bị lược bỏ")
  void giuNull() {
    assertEquals("{\"lyDo\":null}", AuditJson.of("lyDo", null));

    // Trường vắng mặt và trường null cho hai chuỗi khác nhau, nên hai hash khác nhau.
    assertNotEquals(AuditJson.of("lyDo", null), AuditJson.of());
  }

  @Test
  @DisplayName("Dấu nháy kép trong giá trị được thoát — KHÔNG làm hỏng bản ghi")
  void thoatNhayKep() {
    // Đây là lỗi mà lớp này sinh ra để chặn: nối thẳng chuỗi người dùng nhập vào JSON.
    String s = AuditJson.of("lyDo", "Cán bộ ghi \"nhầm\" học kỳ");

    assertEquals("{\"lyDo\":\"Cán bộ ghi \\\"nhầm\\\" học kỳ\"}", s);
  }

  @Test
  @DisplayName("Xuống dòng, tab, dấu chéo ngược đều được thoát")
  void thoatKyTuDieuKhien() {
    assertEquals("{\"x\":\"a\\nb\\tc\\\\d\"}", AuditJson.of("x", "a\nb\tc\\d"));
  }

  @Test
  @DisplayName("Tiếng Việt có dấu ra UTF-8 thô, không escape u-hex")
  void tiengViet() {
    String s = AuditJson.of("hoTen", "Nguyễn Ngọc Hoàng");
    assertEquals("{\"hoTen\":\"Nguyễn Ngọc Hoàng\"}", s);
  }

  @Test
  @DisplayName("Số phần tử lẻ bị từ chối, không lặng lẽ bỏ phần tử cuối")
  void soPhanTuLe() {
    assertThrows(IllegalArgumentException.class, () -> AuditJson.of("a", 1L, "b"));
  }

  @Test
  @DisplayName("Khóa không phải chuỗi bị từ chối")
  void khoaSaiKieu() {
    assertThrows(IllegalArgumentException.class, () -> AuditJson.of(1L, "a"));
  }

  @Test
  @DisplayName("Object rỗng hợp lệ")
  void rong() {
    assertEquals("{}", AuditJson.of());
  }
}
