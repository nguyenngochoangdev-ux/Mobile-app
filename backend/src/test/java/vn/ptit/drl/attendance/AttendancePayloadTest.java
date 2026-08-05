package vn.ptit.drl.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ptit.drl.anchor.AnchorDomain;
import vn.ptit.drl.anchor.LeafHasher;
import vn.ptit.drl.event.Event;
import vn.ptit.drl.identity.Student;

/**
 * Nối {@link AttendancePayload} với bộ test vector.
 *
 * <p>Đây là mắt xích còn thiếu giữa hai thứ đã được test riêng: {@code canonical-vectors.json}
 * chốt rằng một payload cho ra leaf hash nào, còn lớp này chốt rằng backend <b>dựng ra đúng
 * payload đó</b> từ một bản ghi thật. Thiếu mắt xích này thì cả hai bên vẫn xanh trong khi
 * job neo sinh ra payload khác hẳn.
 */
class AttendancePayloadTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HexFormat HEX = HexFormat.of();

  private static JsonNode vector(String id) {
    try (InputStream in =
        AttendancePayloadTest.class.getResourceAsStream("/canonical-vectors.json")) {
      JsonNode doc = MAPPER.readTree(in);
      for (JsonNode v : doc.get("vectors")) {
        if (id.equals(v.get("id").textValue())) {
          return v;
        }
      }
      throw new IllegalStateException("Không thấy vector " + id);
    } catch (Exception e) {
      throw new IllegalStateException("Không đọc được bộ test vector", e);
    }
  }

  private static Attendance record(
      String mssv, long eventId, AttendanceMethod method, Instant checkIn, Instant checkOut,
      String deviceFp, BigDecimal lat, BigDecimal lng, Boolean verified, Boolean geofenceOk,
      String nonceHex) {

    Student s = new Student();
    s.setMssv(mssv);

    Event e = new Event();
    e.setId(eventId);

    Attendance a = new Attendance();
    a.setStudent(s);
    a.setEvent(e);
    a.setMethod(method);
    a.setCheckinAt(checkIn);
    a.setCheckoutAt(checkOut);
    a.setDeviceFp(deviceFp);
    a.setLat(lat);
    a.setLng(lng);
    a.setVerified(verified);
    a.setGeofenceOk(geofenceOk);
    a.setNonce(HEX.parseHex(nonceHex));
    return a;
  }

  @Test
  @DisplayName("Bản ghi QR_SCAN đầy đủ cho ra ĐÚNG leaf hash trong vector")
  void banGhiDayDuKhopVector() {
    JsonNode v = vector("attend-payload-day-du");

    Attendance a = record(
        "B21DCCN042", 7L, AttendanceMethod.QR_SCAN,
        Instant.parse("2026-08-04T17:55:58Z"), Instant.parse("2026-08-04T19:30:00Z"),
        "6f1c2a90-3b4d-4e5f-8a91-2c3d4e5f6a7b",
        // Đúng như JDBC trả về từ DECIMAL(10,7): mang theo số chữ số thập phân của lược đồ.
        new BigDecimal("21.0285000"), new BigDecimal("105.8542000"),
        true, true, "74a40e0f493b112d51f09ade5cab9d44");

    assertEquals(
        v.get("expected").get("leaf").textValue(),
        LeafHasher.leafHex(AnchorDomain.ATTEND, AttendancePayload.of(a)));
  }

  @Test
  @DisplayName("Bản ghi MANUAL toàn null cho ra ĐÚNG leaf hash trong vector")
  void banGhiToanNullKhopVector() {
    JsonNode v = vector("attend-payload-toan-null");

    Attendance a = record(
        "B21DCCN042", 3L, AttendanceMethod.MANUAL,
        Instant.parse("2026-08-04T17:57:04Z"), null,
        null, null, null,
        false, null, "0403c6481c937c9f1ffacbf727fbe58b");

    assertEquals(
        v.get("expected").get("leaf").textValue(),
        LeafHasher.leafHex(AnchorDomain.ATTEND, AttendancePayload.of(a)));
  }

  @Test
  @DisplayName("Tập trường đúng 11, khớp ATTEND_FIELDS phía JS")
  void dungTapTruong() {
    Attendance a = record(
        "B21DCCN001", 1L, AttendanceMethod.QR_SCAN, Instant.now(), null,
        null, null, null, true, null, "00112233445566778899aabbccddeeff");

    Set<String> actual = new TreeSet<>(AttendancePayload.of(a).keySet());
    Set<String> expected = new TreeSet<>(Set.of(
        "checkInAt", "checkOutAt", "deviceFp", "eventId", "geofenceOk",
        "lat", "lng", "method", "nonce", "studentCode", "verified"));

    assertEquals(expected, actual, "lệch tập trường với verifier/src/attend.mjs");
  }

  @Test
  @DisplayName("Trường null GIỮ NGUYÊN, không bị lược bỏ")
  void giuTruongNull() {
    // Trường vắng mặt và trường null cho ra hai hash khác nhau (canonicalization §4 quy
    // tắc 6). Nếu builder lược bỏ null thì verifier — vốn đòi đủ 11 trường — sẽ tính ra
    // hash khác và mọi proof của bản ghi thủ công đều fail.
    Attendance a = record(
        "B21DCCN001", 1L, AttendanceMethod.MANUAL, Instant.now(), null,
        null, null, null, false, null, "00112233445566778899aabbccddeeff");

    Map<String, Object> p = AttendancePayload.of(a);
    for (String key : new String[] {"checkOutAt", "deviceFp", "lat", "lng", "geofenceOk"}) {
      assertTrue(p.containsKey(key), "thiếu khóa " + key);
      assertNull(p.get(key), key + " phải là null");
    }
  }

  @Test
  @DisplayName("Thời gian cắt xuống GIÂY, bỏ phần mili")
  void catXuongGiay() {
    // Cột là DATETIME(3). Không cắt thì bản ghi có phần lẻ ra "...58.123Z" còn bản ghi
    // tròn giây ra "...58Z" — hai định dạng trong cùng một lô, và verifier chỉ dựng lại
    // được một trong hai.
    assertEquals(
        "2026-08-04T17:55:58Z",
        AttendancePayload.isoSeconds(Instant.parse("2026-08-04T17:55:58.123Z")));
    assertEquals(
        "2026-08-04T17:55:58Z",
        AttendancePayload.isoSeconds(Instant.parse("2026-08-04T17:55:58Z")));
    assertNull(AttendancePayload.isoSeconds(null));
  }

  @Test
  @DisplayName("DECIMAL(10,7) có số 0 đuôi KHÔNG làm lệch hash")
  void so0DuoiKhongLamLechHash() {
    // JDBC trả BigDecimal("21.0285000") từ DECIMAL(10,7); JS chỉ có 21.0285.
    // Nếu chỗ nào đó dùng BigDecimal.toString() thì đây là chỗ hash lệch âm thầm.
    Attendance a = record("B21DCCN001", 1L, AttendanceMethod.QR_SCAN,
        Instant.parse("2026-08-04T17:55:58Z"), null, null,
        new BigDecimal("21.0285000"), new BigDecimal("105.8542000"),
        true, true, "00112233445566778899aabbccddeeff");

    Attendance b = record("B21DCCN001", 1L, AttendanceMethod.QR_SCAN,
        Instant.parse("2026-08-04T17:55:58Z"), null, null,
        new BigDecimal("21.0285"), new BigDecimal("105.8542"),
        true, true, "00112233445566778899aabbccddeeff");

    assertEquals(
        LeafHasher.leafHex(AnchorDomain.ATTEND, AttendancePayload.of(a)),
        LeafHasher.leafHex(AnchorDomain.ATTEND, AttendancePayload.of(b)));
  }

  @Test
  @DisplayName("Bản ghi thiếu nonce bị TỪ CHỐI, không neo bừa")
  void thieuNonceBiTuChoi() {
    Attendance a = record("B21DCCN001", 1L, AttendanceMethod.QR_SCAN,
        Instant.now(), null, null, null, null, true, null,
        "00112233445566778899aabbccddeeff");
    a.setNonce(null);

    var e = assertThrows(IllegalStateException.class, () -> AttendancePayload.of(a));
    assertTrue(e.getMessage().contains("nonce"));
  }
}
