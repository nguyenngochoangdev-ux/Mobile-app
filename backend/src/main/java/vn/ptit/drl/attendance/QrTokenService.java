package vn.ptit.drl.attendance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import vn.ptit.drl.common.config.DrlProperties;

/**
 * Sinh và kiểm tra token QR động.
 * <p>
 * Màn hình presenter render lại mã QR mỗi {@code qrSlotSeconds} giây. Ảnh chụp màn hình
 * gửi cho bạn ở nhà sẽ hết hạn trước khi kịp dùng — đây là bước kiểm tra thứ nhất
 * trong năm bước check-in.
 * <pre>
 *   slot  = epochSecond / qrSlotSeconds
 *   mac   = HmacSHA256(event.secretKey, eventId + "|" + slot)
 *   token = base64url(mac[0..12])
 * </pre>
 * Cắt còn 12 byte để QR nhỏ, quét nhanh. 96 bit vẫn quá đủ: token chỉ sống 10–20 giây
 * và kẻ tấn công không có kênh nào để thử hàng loạt.
 */
@Service
@RequiredArgsConstructor
public class QrTokenService {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final int TOKEN_BYTES = 12;

    private final DrlProperties props;

    public long currentSlot() {
        return slotAt(Instant.now());
    }

    public long slotAt(Instant time) {
        return time.getEpochSecond() / props.attendance().qrSlotSeconds();
    }

    public String generate(Long eventId, byte[] secretKey, long slot) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALGO));
            byte[] full = mac.doFinal((eventId + "|" + slot).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Arrays.copyOf(full, TOKEN_BYTES));
        } catch (Exception ex) {
            throw new IllegalStateException("Không sinh được token QR", ex);
        }
    }

    /**
     * Kiểm tra token với slot người dùng gửi lên.
     * <p>
     * Chấp nhận slot trong khoảng {@code [current - tolerance, current]} để bù lệch đồng
     * hồ giữa thiết bị. KHÔNG chấp nhận slot tương lai: đồng hồ chạy nhanh là chuyện có
     * thật, nhưng cho phép slot tương lai mở đường cho việc sinh trước token.
     *
     * @param claimedSlot slot mà client đọc được từ QR
     */
    public boolean verify(Long eventId, byte[] secretKey, long claimedSlot, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        long current = currentSlot();
        int tolerance = props.attendance().qrSlotTolerance();

        if (claimedSlot > current || claimedSlot < current - tolerance) {
            return false;
        }

        String expected = generate(eventId, secretKey, claimedSlot);
        // So sánh thời gian hằng số — tránh rò rỉ qua timing.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Kiểm tra token cho bản ghi offline đồng bộ muộn.
     * <p>
     * Hội trường thường mất sóng nên chế độ offline là bắt buộc, không phải tính năng phụ.
     * Cửa sổ rộng hơn nhiều ({@code offlineWindowHours}) nhưng token vẫn phải hợp lệ với
     * đúng slot đã quét — nghĩa là vẫn phải có mặt lúc mã QR đó đang hiển thị.
     */
    public boolean verifyOffline(Long eventId, byte[] secretKey, long claimedSlot, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        long current = currentSlot();
        long windowSlots = (long) props.attendance().offlineWindowHours()
                * 3600 / props.attendance().qrSlotSeconds();

        if (claimedSlot > current || claimedSlot < current - windowSlots) {
            return false;
        }

        String expected = generate(eventId, secretKey, claimedSlot);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    public Instant slotStart(long slot) {
        return Instant.ofEpochSecond(slot * props.attendance().qrSlotSeconds());
    }
}
