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
 * Mã QR của <b>sinh viên</b> — dùng cho luồng đảo chiều: sinh viên hiển thị, cán bộ quét.
 *
 * <p>Đây là phương án 3 của {@code PROJECT.md} §2.4, và nó phải làm <b>bất kể</b> chọn thiết
 * bị demo nào: nó là phương án cứu khi hội trường mất sóng hoặc camera máy sinh viên hỏng.
 *
 * <h2>Ngược chiều với {@link QrTokenService}</h2>
 *
 * <pre>
 *   QrTokenService   : token thuộc về SỰ KIỆN, sinh viên quét   → method QR_SCAN
 *   StudentQrService : token thuộc về SINH VIÊN, cán bộ quét    → method QR_SHOW
 * </pre>
 *
 * <p>Công thức song song nhau, cùng cơ chế slot:
 *
 * <pre>
 *   mac   = HmacSHA256(khoaDanXuat, studentId + "|" + slot)
 *   token = base64url(mac[0..12])
 *   QR    = "DRL1:" + studentId + ":" + slot + ":" + token
 * </pre>
 *
 * <h2>Khóa dẫn xuất, không thêm bí mật mới</h2>
 *
 * <p>Khóa ký lấy từ {@code drl.jwt.secret} qua một bước dẫn xuất có nhãn:
 * {@code HmacSHA256(jwtSecret, "drl:student-qr:v1")}. Đây là tách khóa theo mục đích đúng
 * chuẩn — token QR không bao giờ ký được bằng khóa JWT và ngược lại.
 *
 * <p>Cố ý <b>không</b> thêm một bí mật mới vào {@code .env}: {@code PROJECT.md} §2.6 đã ghi
 * việc giữ và sao lưu khóa là điểm yếu của đề tài, nên thêm khóa thứ hai để giải quyết một
 * việc mà dẫn xuất làm được là đi sai hướng.
 *
 * <h2>Ba mức tươi, không phải hai — chỗ này quyết định cột `verified`</h2>
 *
 * <p>Xem {@link Freshness}. Tóm tắt: token còn trong dung sai slot thì máy chứng minh được
 * sinh viên vừa đăng nhập vài giây trước ({@code verified = true}); token cũ hơn nhưng còn
 * trong cửa sổ offline vẫn nhận nhưng {@code verified = false}, vì lúc đó chỉ có mắt cán bộ
 * chứng minh sự có mặt.
 */
@Service
@RequiredArgsConstructor
public class StudentQrService {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final int TOKEN_BYTES = 12;

    /** Nhãn tách khóa. Đổi nhãn là vô hiệu mọi QR đang lưu — chỉ đổi khi cố ý xoay khóa. */
    private static final byte[] KEY_LABEL =
            "drl:student-qr:v1".getBytes(StandardCharsets.UTF_8);

    /** Tiền tố để máy quét phân biệt QR của hệ thống với QR bất kỳ. */
    public static final String PREFIX = "DRL1";

    private final DrlProperties props;
    private final QrTokenService qrTokenService;

    /**
     * Mức "tươi" của token, quyết định cột {@code verified} của bản ghi.
     *
     * <p>Phân ba mức chứ không phải nhận/từ chối là có chủ ý — nó chính là thứ làm cho chỉ số
     * chất lượng dữ liệu của đề tài có nghĩa ({@code PROJECT.md} §10).
     */
    public enum Freshness {
        /**
         * Trong dung sai slot (~10–20 giây). Máy chứng minh được sinh viên vừa đăng nhập
         * ngay trước đó → {@code verified = true}.
         */
        FRESH,
        /**
         * Quá dung sai nhưng còn trong cửa sổ offline. Vẫn nhận — hội trường mất sóng thì
         * máy sinh viên không lấy được token mới, và đó chính là tình huống luồng này sinh
         * ra để cứu. Nhưng {@code verified = false}: lúc này chỉ có mắt cán bộ chứng minh
         * sự có mặt, không phải máy.
         */
        STALE,
        /** Sai chữ ký, sai định dạng, hoặc quá cũ. Từ chối. */
        INVALID
    }

    /** Nội dung đã giải mã từ QR của sinh viên. */
    public record StudentQr(Long studentId, long slot, String token) {

        /** Chuỗi đặt vào mã QR. */
        public String encode() {
            return PREFIX + ":" + studentId + ":" + slot + ":" + token;
        }
    }

    // ------------------------------------------------------------------ sinh

    public long currentSlot() {
        return qrTokenService.currentSlot();
    }

    public String generate(Long studentId, long slot) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(derivedKey(), HMAC_ALGO));
            byte[] full = mac.doFinal((studentId + "|" + slot).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Arrays.copyOf(full, TOKEN_BYTES));
        } catch (Exception ex) {
            throw new IllegalStateException("Không sinh được token QR sinh viên", ex);
        }
    }

    /** QR hiện tại của một sinh viên. */
    public StudentQr current(Long studentId) {
        long slot = currentSlot();
        return new StudentQr(studentId, slot, generate(studentId, slot));
    }

    /**
     * Thời điểm token của slot này thôi được coi là {@link Freshness#FRESH}.
     *
     * <p>Token còn tươi khi {@code slot >= current − tolerance}, tức slot hiện tại lớn nhất
     * còn chấp nhận là {@code slot + tolerance}. Nó hết tươi ngay khi slot kế tiếp bắt đầu.
     *
     * <p>Máy sinh viên dùng giá trị này để biết khi nào phải xin token mới.
     */
    public Instant freshUntil(long slot) {
        return qrTokenService.slotStart(slot + props.attendance().qrSlotTolerance() + 1L);
    }

    // ------------------------------------------------------------------ đọc

    /**
     * Giải mã chuỗi quét được. Trả {@code null} nếu không phải QR của hệ thống — máy quét
     * gặp đủ loại mã vạch, không phải cái nào cũng của mình.
     */
    public StudentQr decode(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.trim().split(":");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return null;
        }
        try {
            return new StudentQr(Long.parseLong(parts[1]), Long.parseLong(parts[2]), parts[3]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Kiểm tra token và trả về mức tươi.
     *
     * <p>Không chấp nhận slot tương lai, giống {@link QrTokenService#verify}: đồng hồ chạy
     * nhanh là chuyện có thật, nhưng cho phép slot tương lai mở đường cho việc sinh trước
     * hàng loạt token rồi phát tán.
     */
    public Freshness verify(StudentQr qr) {
        if (qr == null || qr.token() == null || qr.token().isBlank()) {
            return Freshness.INVALID;
        }

        long current = currentSlot();
        if (qr.slot() > current) {
            return Freshness.INVALID;
        }

        String expected = generate(qr.studentId(), qr.slot());
        boolean macOk = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                qr.token().getBytes(StandardCharsets.UTF_8));
        if (!macOk) {
            return Freshness.INVALID;
        }

        if (qr.slot() >= current - props.attendance().qrSlotTolerance()) {
            return Freshness.FRESH;
        }

        long windowSlots = (long) props.attendance().offlineWindowHours()
                * 3600 / props.attendance().qrSlotSeconds();
        return qr.slot() >= current - windowSlots ? Freshness.STALE : Freshness.INVALID;
    }

    // ------------------------------------------------------------------ khóa

    /**
     * {@code HmacSHA256(jwtSecret, "drl:student-qr:v1")} — tách khóa theo mục đích.
     *
     * <p>Tính lại mỗi lần thay vì cache: nó rẻ (một lần HMAC), và giữ khóa dẫn xuất trong
     * một trường tĩnh chỉ tạo thêm một bản sao của bí mật nằm trong bộ nhớ suốt đời tiến trình.
     */
    private byte[] derivedKey() {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(
                    props.jwt().secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            return mac.doFinal(KEY_LABEL);
        } catch (Exception ex) {
            throw new IllegalStateException("Không dẫn xuất được khóa QR sinh viên", ex);
        }
    }
}
