package vn.ptit.drl.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vn.ptit.drl.common.config.DrlProperties;

/**
 * Cơ chế QR động là lớp kiểm tra thứ nhất trong năm bước check-in. Mỗi khẳng định ở đây
 * tương ứng một dòng trong bảng mô hình đe dọa (chương 11.2 báo cáo).
 * <p>
 * Lưu ý phạm vi: các test này chứng minh token cũ và token bịa bị từ chối. Chúng KHÔNG
 * chứng minh hệ thống chống được điểm danh hộ nói chung — chuyển tiếp mã QR trong vòng
 * dung sai slot vẫn qua được, và đó là hạn chế đã ghi trong bảng threat model.
 */
class QrTokenServiceTest {

    private static final byte[] SECRET_A = "secret-cua-su-kien-A-32-byte-abc".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECRET_B = "secret-cua-su-kien-B-32-byte-xyz".getBytes(StandardCharsets.UTF_8);

    private QrTokenService service;

    @BeforeEach
    void setUp() {
        var attendance = new DrlProperties.Attendance(10, 1, 24, false, true);
        var props = new DrlProperties(null, attendance, null, null, null);
        service = new QrTokenService(props);
    }

    @Test
    @DisplayName("Token sinh ra tự kiểm tra được với slot hiện tại")
    void tokenHopLeVoiSlotHienTai() {
        long slot = service.currentSlot();
        String token = service.generate(1L, SECRET_A, slot);

        assertThat(service.verify(1L, SECRET_A, slot, token)).isTrue();
    }

    @Test
    @DisplayName("Token bịa đặt bị từ chối")
    void tokenBiaDatBiTuChoi() {
        long slot = service.currentSlot();

        assertThat(service.verify(1L, SECRET_A, slot, "AAAAAAAAAAAAAAAA")).isFalse();
        assertThat(service.verify(1L, SECRET_A, slot, "")).isFalse();
        assertThat(service.verify(1L, SECRET_A, slot, null)).isFalse();
    }

    @Test
    @DisplayName("Ảnh chụp màn hình QR cũ hết hiệu lực ngoài dung sai — chống chia sẻ ảnh")
    void anhChupManHinhCuHetHieuLuc() {
        long current = service.currentSlot();

        // slot - 1 vẫn chấp nhận: bù lệch đồng hồ giữa các thiết bị.
        String tokenTruoc1 = service.generate(1L, SECRET_A, current - 1);
        assertThat(service.verify(1L, SECRET_A, current - 1, tokenTruoc1)).isTrue();

        // slot - 2 trở đi bị từ chối: ảnh chụp gửi cho bạn ở nhà đã quá hạn.
        String tokenTruoc2 = service.generate(1L, SECRET_A, current - 2);
        assertThat(service.verify(1L, SECRET_A, current - 2, tokenTruoc2)).isFalse();

        String tokenCuLau = service.generate(1L, SECRET_A, current - 100);
        assertThat(service.verify(1L, SECRET_A, current - 100, tokenCuLau)).isFalse();
    }

    @Test
    @DisplayName("Slot tương lai bị từ chối — chống sinh trước token")
    void slotTuongLaiBiTuChoi() {
        long current = service.currentSlot();
        String tokenTuongLai = service.generate(1L, SECRET_A, current + 1);

        assertThat(service.verify(1L, SECRET_A, current + 1, tokenTuongLai)).isFalse();
    }

    @Test
    @DisplayName("Secret riêng từng sự kiện: lộ một secret không ảnh hưởng sự kiện khác")
    void secretRiengTungSuKien() {
        long slot = service.currentSlot();
        String tokenA = service.generate(1L, SECRET_A, slot);

        // Cùng eventId nhưng secret khác -> không dùng lại được.
        assertThat(service.verify(1L, SECRET_B, slot, tokenA)).isFalse();
    }

    @Test
    @DisplayName("Token gắn với eventId: không dùng token sự kiện này cho sự kiện khác")
    void tokenGanVoiEventId() {
        long slot = service.currentSlot();
        String tokenEvent1 = service.generate(1L, SECRET_A, slot);

        assertThat(service.verify(2L, SECRET_A, slot, tokenEvent1)).isFalse();
    }

    @Test
    @DisplayName("Cửa sổ offline rộng hơn nhiều nhưng vẫn phải đúng token của slot đã quét")
    void cuaSoOffline() {
        long current = service.currentSlot();
        // 2 giờ trước — ngoài dung sai online, trong cửa sổ offline 24 giờ.
        long slotCu = current - (2 * 3600 / 10);
        String token = service.generate(1L, SECRET_A, slotCu);

        assertThat(service.verify(1L, SECRET_A, slotCu, token)).isFalse();
        assertThat(service.verifyOffline(1L, SECRET_A, slotCu, token)).isTrue();

        // Ngoài cửa sổ 24 giờ thì offline cũng từ chối.
        long slotQuaCu = current - (25 * 3600 / 10);
        String tokenQuaCu = service.generate(1L, SECRET_A, slotQuaCu);
        assertThat(service.verifyOffline(1L, SECRET_A, slotQuaCu, tokenQuaCu)).isFalse();

        // Token sai vẫn bị từ chối trong chế độ offline.
        assertThat(service.verifyOffline(1L, SECRET_A, slotCu, "khong-phai-token")).isFalse();
    }

    @Test
    @DisplayName("Token đổi mỗi slot — presenter render lại mã mới")
    void tokenDoiMoiSlot() {
        long slot = service.currentSlot();

        assertThat(service.generate(1L, SECRET_A, slot))
                .isNotEqualTo(service.generate(1L, SECRET_A, slot + 1));
    }
}
