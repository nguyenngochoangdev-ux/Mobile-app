package vn.ptit.drl.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import vn.ptit.drl.attendance.StudentQrService.Freshness;
import vn.ptit.drl.common.config.DrlProperties;

/**
 * Test QR sinh viên — luồng đảo chiều (PROJECT.md §2.4 phương án 3).
 *
 * <p>Đặc biệt chú ý nhóm {@link BaMucTuoi}: nó chốt việc token cũ vẫn được nhận nhưng
 * {@code verified = false}. Gộp hai mức đó lại thành "hợp lệ / không hợp lệ" sẽ làm hỏng chỉ
 * số chất lượng dữ liệu mà đề tài nêu làm đóng góp.
 */
class StudentQrServiceTest {

    private static final String SECRET = "khoa-jwt-test-du-dai-de-lam-khoa-hmac-256-bit-tro-len";
    private static final long STUDENT = 502L;

    private StudentQrService service;
    private QrTokenService qrTokenService;

    private static DrlProperties props(int slotSeconds, int tolerance, int offlineHours) {
        return new DrlProperties(
                new DrlProperties.Jwt(SECRET, 30, 14),
                new DrlProperties.Attendance(slotSeconds, tolerance, offlineHours, false, true),
                null, null, null);
    }

    @BeforeEach
    void setUp() {
        var p = props(10, 1, 24);
        qrTokenService = new QrTokenService(p);
        service = new StudentQrService(p, qrTokenService);
    }

    // ------------------------------------------------------------------ sinh

    @Test
    @DisplayName("Token tự kiểm tra được với slot hiện tại")
    void tokenHopLeVoiSlotHienTai() {
        var qr = service.current(STUDENT);
        assertEquals(Freshness.FRESH, service.verify(qr));
    }

    @Test
    @DisplayName("Chuỗi QR mã hoá rồi giải mã ra đúng nội dung cũ")
    void maHoaGiaiMaKhepKin() {
        var qr = service.current(STUDENT);
        var decoded = service.decode(qr.encode());

        assertEquals(qr.studentId(), decoded.studentId());
        assertEquals(qr.slot(), decoded.slot());
        assertEquals(qr.token(), decoded.token());
    }

    @Test
    @DisplayName("Mỗi sinh viên một token khác nhau ở cùng slot")
    void moiSinhVienMotToken() {
        long slot = service.currentSlot();
        assertNotEquals(service.generate(1L, slot), service.generate(2L, slot));
    }

    @Test
    @DisplayName("Cùng sinh viên, khác slot thì khác token")
    void doiSlotThiDoiToken() {
        long slot = service.currentSlot();
        assertNotEquals(service.generate(STUDENT, slot), service.generate(STUDENT, slot - 1));
    }

    // ------------------------------------------------------------- giải mã

    @Nested
    @DisplayName("Giải mã mã quét được")
    class GiaiMa {

        @Test
        @DisplayName("Mã không phải của hệ thống trả về null, không ném lỗi")
        void maLaTraVeNull() {
            // Máy quét gặp đủ loại mã vạch — mã sản phẩm, link, vCard. Không phải cái nào
            // cũng của mình, và ném lỗi ở đây sẽ làm màn hình cán bộ đỏ lòm vô cớ.
            assertNull(service.decode("https://example.com"));
            assertNull(service.decode("8934563138165"));
            assertNull(service.decode(""));
            assertNull(service.decode(null));
        }

        @Test
        @DisplayName("Sai số phần hoặc sai tiền tố trả về null")
        void saiDinhDangTraVeNull() {
            assertNull(service.decode("DRL1:502:12345"));
            assertNull(service.decode("DRL1:502:12345:abc:thua"));
            assertNull(service.decode("DRL2:502:12345:abc"));
        }

        @Test
        @DisplayName("studentId hoặc slot không phải số trả về null")
        void khongPhaiSoTraVeNull() {
            assertNull(service.decode("DRL1:abc:12345:token"));
            assertNull(service.decode("DRL1:502:xyz:token"));
        }
    }

    // ------------------------------------------------- NHÓM QUAN TRỌNG NHẤT

    @Nested
    @DisplayName("Ba mức tươi — quyết định cột verified")
    class BaMucTuoi {

        @Test
        @DisplayName("Trong dung sai slot → FRESH")
        void trongDungSaiThiTuoi() {
            long slot = service.currentSlot();
            for (long s = slot; s >= slot - 1; s--) { // tolerance = 1
                var qr = new StudentQrService.StudentQr(STUDENT, s, service.generate(STUDENT, s));
                assertEquals(Freshness.FRESH, service.verify(qr), "slot " + s);
            }
        }

        @Test
        @DisplayName("Quá dung sai nhưng trong cửa sổ offline → STALE, vẫn nhận")
        void quaDungSaiThiCu() {
            // Đây là tình huống luồng này sinh ra để cứu: hội trường mất sóng nên máy sinh
            // viên không xin được token mới. Từ chối thẳng là giết mất phương án dự phòng.
            long slot = service.currentSlot() - 100;
            var qr = new StudentQrService.StudentQr(STUDENT, slot, service.generate(STUDENT, slot));

            assertEquals(Freshness.STALE, service.verify(qr));
        }

        @Test
        @DisplayName("Quá cửa sổ offline → INVALID")
        void quaCuaSoOfflineThiTuChoi() {
            // 24 giờ / 10 giây = 8640 slot.
            long slot = service.currentSlot() - 8641;
            var qr = new StudentQrService.StudentQr(STUDENT, slot, service.generate(STUDENT, slot));

            assertEquals(Freshness.INVALID, service.verify(qr));
        }

        @Test
        @DisplayName("Biên cửa sổ offline: slot cuối cùng còn nhận")
        void bienCuaSoOffline() {
            long slot = service.currentSlot() - 8640;
            var qr = new StudentQrService.StudentQr(STUDENT, slot, service.generate(STUDENT, slot));

            assertEquals(Freshness.STALE, service.verify(qr));
        }
    }

    // ------------------------------------------------------------ từ chối

    @Test
    @DisplayName("Slot TƯƠNG LAI bị từ chối — chặn sinh trước token hàng loạt")
    void slotTuongLaiBiTuChoi() {
        // Đồng hồ chạy nhanh là chuyện có thật, nhưng cho phép slot tương lai nghĩa là ai
        // chiếm được tài khoản có thể sinh sẵn token cho cả học kỳ rồi phát tán.
        long slot = service.currentSlot() + 1;
        var qr = new StudentQrService.StudentQr(STUDENT, slot, service.generate(STUDENT, slot));

        assertEquals(Freshness.INVALID, service.verify(qr));
    }

    @Test
    @DisplayName("Token bịa bị từ chối")
    void tokenBiaBiTuChoi() {
        var qr = new StudentQrService.StudentQr(STUDENT, service.currentSlot(), "AAAAAAAAAAAAAAAA");
        assertEquals(Freshness.INVALID, service.verify(qr));
    }

    @Test
    @DisplayName("Đổi studentId trong QR làm token hỏng — không mạo danh được")
    void doiStudentIdThiHong() {
        // Kịch bản thật: sinh viên A sửa QR của mình thành studentId của B để điểm danh hộ.
        var qr = service.current(STUDENT);
        var giaMao = new StudentQrService.StudentQr(999L, qr.slot(), qr.token());

        assertEquals(Freshness.INVALID, service.verify(giaMao));
    }

    @Test
    @DisplayName("Token rỗng hoặc null bị từ chối")
    void tokenRongBiTuChoi() {
        long slot = service.currentSlot();
        assertEquals(Freshness.INVALID,
                service.verify(new StudentQrService.StudentQr(STUDENT, slot, "")));
        assertEquals(Freshness.INVALID,
                service.verify(new StudentQrService.StudentQr(STUDENT, slot, null)));
        assertEquals(Freshness.INVALID, service.verify(null));
    }

    // ------------------------------------------------------------ tách khóa

    @Test
    @DisplayName("Token QR sinh viên KHÁC token QR sự kiện dù cùng số và cùng secret")
    void tachKhoaTheoMucDich() {
        // Khóa QR sinh viên là HmacSHA256(jwtSecret, "drl:student-qr:v1"), không phải chính
        // jwtSecret. Nếu hai bên trùng token thì một token của sự kiện có thể dùng làm token
        // của sinh viên có cùng id — lỗi tách khóa kinh điển.
        long slot = service.currentSlot();
        byte[] secretAsEventKey = SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertNotEquals(
                qrTokenService.generate(STUDENT, secretAsEventKey, slot),
                service.generate(STUDENT, slot));
    }

    @Test
    @DisplayName("Đổi jwt secret là vô hiệu mọi QR cũ")
    void doiSecretThiVoHieuQrCu() {
        var qr = service.current(STUDENT);

        var khac = new StudentQrService(
                new DrlProperties(
                        new DrlProperties.Jwt("mot-khoa-hoan-toan-khac-de-kiem-tra-viec-xoay-khoa", 30, 14),
                        new DrlProperties.Attendance(10, 1, 24, false, true),
                        null, null, null),
                qrTokenService);

        assertEquals(Freshness.INVALID, khac.verify(qr));
    }

    @Test
    @DisplayName("freshUntil nằm trong tương lai và khớp dung sai đã cấu hình")
    void thoiDiemHetTuoi() {
        long slot = service.currentSlot();
        var until = service.freshUntil(slot);

        assertTrue(until.isAfter(java.time.Instant.now()), "freshUntil phải ở tương lai");
        // tolerance = 1, slot = 10s → token còn tươi tối đa 20 giây kể từ đầu slot.
        assertEquals(qrTokenService.slotStart(slot).plusSeconds(20), until);
    }
}
