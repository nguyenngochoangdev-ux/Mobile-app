package vn.ptit.drl.attendance;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.ptit.drl.common.GeoUtil;
import vn.ptit.drl.common.config.DrlProperties;
import vn.ptit.drl.common.web.BusinessException;
import vn.ptit.drl.common.web.NotFoundException;
import vn.ptit.drl.event.Event;
import vn.ptit.drl.event.EventRepository;
import vn.ptit.drl.event.EventStatus;
import vn.ptit.drl.event.RegistrationRepository;
import vn.ptit.drl.event.RegistrationStatus;
import vn.ptit.drl.identity.DeviceStatus;
import vn.ptit.drl.identity.StudentDeviceRepository;
import vn.ptit.drl.identity.StudentRepository;

/**
 * Năm bước kiểm tra khi check-in.
 * <p>
 * Phần này chiếm khoảng 80% giá trị thực tiễn của hệ thống mà không dùng một chút
 * blockchain nào. Nhưng nó là đầu vào cho chuỗi: chất lượng dữ liệu ở tầng thu thập
 * cộng với tính bất biến ở tầng lưu trữ — thiếu một trong hai thì cái còn lại vô nghĩa.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AttendanceRepository attendanceRepository;
    private final EventRepository eventRepository;
    private final StudentRepository studentRepository;
    private final StudentDeviceRepository deviceRepository;
    private final RegistrationRepository registrationRepository;
    private final QrTokenService qrTokenService;
    private final DrlProperties props;

    public record CheckinCommand(Long eventId, Long studentId, long slot, String token,
                                 String deviceFp, BigDecimal lat, BigDecimal lng,
                                 Instant scannedAt, boolean offline) {}

    @Transactional
    public Attendance checkin(CheckinCommand cmd) {
        Event event = eventRepository.findById(cmd.eventId())
                .orElseThrow(() -> new NotFoundException("sự kiện", cmd.eventId()));

        // --- Bước 1: token HMAC hợp lệ và slot còn hiệu lực ---
        // Chống chia sẻ ảnh chụp màn hình mã QR.
        boolean tokenOk = cmd.offline()
                ? qrTokenService.verifyOffline(event.getId(), event.getSecretKey(), cmd.slot(), cmd.token())
                : qrTokenService.verify(event.getId(), event.getSecretKey(), cmd.slot(), cmd.token());
        if (!tokenOk) {
            throw new BusinessException(cmd.offline()
                    ? "Mã QR không hợp lệ hoặc đã quá cửa sổ đồng bộ offline"
                    : "Mã QR không hợp lệ hoặc đã hết hạn. Quét lại mã đang hiển thị.");
        }

        // --- Bước 2: thiết bị khớp thiết bị đã đăng ký ---
        // Chống mượn tài khoản. Bỏ bước này thì toàn bộ cơ chế QR động vô nghĩa.
        if (cmd.deviceFp() == null || cmd.deviceFp().isBlank()) {
            throw new BusinessException("Thiếu định danh thiết bị");
        }
        boolean deviceOk = deviceRepository.existsByStudentIdAndDeviceFpAndStatus(
                cmd.studentId(), cmd.deviceFp(), DeviceStatus.ACTIVE);
        if (!deviceOk) {
            throw new BusinessException(
                    "Thiết bị này chưa được duyệt cho tài khoản của bạn. "
                    + "Đăng ký thiết bị và chờ cán bộ duyệt.");
        }

        // --- Bước 3: đã đăng ký sự kiện, nếu sự kiện yêu cầu đăng ký trước ---
        // Sự kiện có giới hạn số lượng thì bắt buộc phải đăng ký trước.
        if (event.getCapacity() != null) {
            boolean registered = registrationRepository
                    .findByEventIdAndStudentId(cmd.eventId(), cmd.studentId())
                    .filter(r -> r.getStatus() == RegistrationStatus.REGISTERED)
                    .isPresent();
            if (!registered) {
                throw new BusinessException("Sự kiện này yêu cầu đăng ký trước");
            }
        }

        // --- Bước 4: geofence — CẢNH BÁO MỀM, không chặn ---
        // GPS trong nhà rất kém chính xác. Dùng làm tín hiệu phụ đánh dấu bản ghi cần
        // xem lại, không dùng để từ chối check-in.
        Boolean geofenceOk = GeoUtil.withinRadius(event.getLat(), event.getLng(),
                event.getRadiusM(), cmd.lat(), cmd.lng());
        if (props.attendance().geofenceBlocking()
                && Boolean.FALSE.equals(geofenceOk)) {
            throw new BusinessException("Bạn đang ở ngoài khu vực sự kiện");
        }

        // --- Bước 5: chưa check-in trước đó ---
        if (attendanceRepository.existsByEventIdAndStudentId(cmd.eventId(), cmd.studentId())) {
            throw new BusinessException("Bạn đã điểm danh sự kiện này rồi");
        }

        if (event.getStatus() == EventStatus.DRAFT) {
            throw new BusinessException("Sự kiện chưa mở");
        }

        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);

        Attendance attendance = Attendance.builder()
                .event(event)
                .student(studentRepository.findById(cmd.studentId())
                        .orElseThrow(() -> new NotFoundException("sinh viên", cmd.studentId())))
                .checkinAt(cmd.scannedAt() == null ? Instant.now() : cmd.scannedAt())
                .method(cmd.offline() ? AttendanceMethod.OFFLINE_SYNC : AttendanceMethod.QR_SCAN)
                .deviceFp(cmd.deviceFp())
                .lat(cmd.lat())
                .lng(cmd.lng())
                .qrSlot(cmd.slot())
                .verified(true)
                .geofenceOk(geofenceOk)
                .nonce(nonce)
                .build();

        return attendanceRepository.save(attendance);
    }

    @Transactional
    public Attendance checkout(Long eventId, Long studentId) {
        Attendance attendance = attendanceRepository
                .findByEventIdAndStudentId(eventId, studentId)
                .orElseThrow(() -> new NotFoundException("Bạn chưa check-in sự kiện này"));

        if (attendance.getCheckoutAt() != null) {
            throw new BusinessException("Bạn đã check-out rồi");
        }
        attendance.setCheckoutAt(Instant.now());
        return attendanceRepository.save(attendance);
    }

    /**
     * Cán bộ điểm danh tay. {@code verified = false} — đây chính là vấn đề oracle:
     * blockchain sẽ bảo toàn vĩnh viễn cả bản ghi sai nếu cán bộ nhập sai từ đầu.
     * Giữ cờ này tách bạch để phần đánh giá còn đếm được bao nhiêu bản ghi là thủ công.
     */
    @Transactional
    public Attendance manualCheckin(Long eventId, Long studentId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("sự kiện", eventId));

        if (attendanceRepository.existsByEventIdAndStudentId(eventId, studentId)) {
            throw new BusinessException("Sinh viên đã điểm danh sự kiện này rồi");
        }

        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);

        return attendanceRepository.save(Attendance.builder()
                .event(event)
                .student(studentRepository.findById(studentId)
                        .orElseThrow(() -> new NotFoundException("sinh viên", studentId)))
                .checkinAt(Instant.now())
                .method(AttendanceMethod.MANUAL)
                .verified(false)
                .nonce(nonce)
                .build());
    }
}
