package vn.ptit.drl.attendance;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.ptit.drl.audit.AuditJson;
import vn.ptit.drl.audit.AuditService;
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
    private final AuditService audit;
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
     * Cán bộ quét mã QR do sinh viên hiển thị — <b>luồng đảo chiều</b>.
     *
     * <p>{@code PROJECT.md} §2.4 phương án 3. Đây là phương án cứu, và nó phải tồn tại
     * <b>bất kể</b> chọn thiết bị demo nào: hội trường mất sóng, camera máy sinh viên hỏng,
     * hoặc máy sinh viên hết pin thì luồng xuôi chết hoàn toàn.
     *
     * <h3>Ba bước kiểm tra, không phải năm — và phải nói rõ vì sao</h3>
     *
     * <p>So với {@link #checkin}, luồng này <b>mất hai bước</b>:
     *
     * <ul>
     *   <li><b>Không có device binding.</b> Máy sinh viên không tham gia giao dịch; máy quét
     *       là máy cán bộ. Không có gì để đối chiếu với {@code student_devices}.
     *   <li><b>Token không gắn với sự kiện.</b> Token thuộc về sinh viên, còn sự kiện do cán
     *       bộ chọn trên máy mình. Nghĩa là một ảnh chụp QR của sinh viên, trong thời hạn còn
     *       hiệu lực, có thể bị nộp cho <b>sự kiện khác</b>.
     * </ul>
     *
     * <p>Cả hai đều <b>không</b> làm rộng thêm bề mặt tấn công so với hiện trạng, vì cán bộ
     * đã có {@link #manualCheckin} — họ điểm danh được cho bất kỳ ai mà không cần mã nào.
     * Luồng này chặt hơn điểm danh tay ở một điểm thật: mã QR <b>không giả được</b>, nên cán
     * bộ không thể gõ nhầm MSSV hay bị đưa một mã bịa.
     *
     * <p>Thứ nó <b>không</b> chứng minh là sự có mặt. Người cầm ảnh chụp QR của bạn mình vẫn
     * qua được — chỉ có mắt cán bộ chặn được, đúng như kiểm tra thẻ sinh viên. Vì vậy dòng
     * này trong bảng threat model là <b>"Tăng chi phí"</b>, không phải "Ngăn".
     *
     * @param freshness kết quả kiểm tra token, quyết định cột {@code verified}. Xem
     *     {@link StudentQrService.Freshness} — token còn tươi thì máy chứng minh được sinh
     *     viên vừa đăng nhập vài giây trước; token cũ thì chỉ có cán bộ chứng minh.
     */
    @Transactional
    public Attendance checkinByStaffScan(Long eventId, Long studentId, long slot,
                                         StudentQrService.Freshness freshness,
                                         BigDecimal lat, BigDecimal lng) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("sự kiện", eventId));

        if (event.getStatus() == EventStatus.DRAFT) {
            throw new BusinessException("Sự kiện chưa mở");
        }

        // Giữ nguyên hai bước còn áp dụng được của luồng xuôi.
        if (event.getCapacity() != null) {
            boolean registered = registrationRepository
                    .findByEventIdAndStudentId(eventId, studentId)
                    .filter(r -> r.getStatus() == RegistrationStatus.REGISTERED)
                    .isPresent();
            if (!registered) {
                throw new BusinessException("Sự kiện này yêu cầu đăng ký trước");
            }
        }
        if (attendanceRepository.existsByEventIdAndStudentId(eventId, studentId)) {
            throw new BusinessException("Sinh viên đã điểm danh sự kiện này rồi");
        }

        // Toạ độ lấy từ máy CÁN BỘ. Ở luồng này nó đáng tin hơn luồng xuôi: cán bộ đứng tại
        // điểm tổ chức, còn máy sinh viên thì ở đâu cũng có thể bịa toạ độ.
        Boolean geofenceOk = GeoUtil.withinRadius(event.getLat(), event.getLng(),
                event.getRadiusM(), lat, lng);

        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);

        return attendanceRepository.save(Attendance.builder()
                .event(event)
                .student(studentRepository.findById(studentId)
                        .orElseThrow(() -> new NotFoundException("sinh viên", studentId)))
                .checkinAt(Instant.now())
                .method(AttendanceMethod.QR_SHOW)
                .qrSlot(slot)
                .lat(lat)
                .lng(lng)
                // Chỉ token còn TƯƠI mới cho verified = true. Token cũ vẫn nhận (đó là mục
                // đích của luồng cứu) nhưng phải đếm được riêng — nếu không thì chỉ số
                // "bao nhiêu phần trăm dữ liệu được máy xác thực" mất hết ý nghĩa.
                .verified(freshness == StudentQrService.Freshness.FRESH)
                .geofenceOk(geofenceOk)
                .nonce(nonce)
                .build());
    }

    /**
     * Cán bộ điểm danh tay. {@code verified = false} — đây chính là vấn đề oracle:
     * blockchain sẽ bảo toàn vĩnh viễn cả bản ghi sai nếu cán bộ nhập sai từ đầu.
     * Giữ cờ này tách bạch để phần đánh giá còn đếm được bao nhiêu bản ghi là thủ công.
     */
    @Transactional
    public Attendance manualCheckin(Long eventId, Long studentId, Long actorId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("sự kiện", eventId));

        if (attendanceRepository.existsByEventIdAndStudentId(eventId, studentId)) {
            throw new BusinessException("Sinh viên đã điểm danh sự kiện này rồi");
        }

        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);

        Attendance a = attendanceRepository.save(Attendance.builder()
                .event(event)
                .student(studentRepository.findById(studentId)
                        .orElseThrow(() -> new NotFoundException("sinh viên", studentId)))
                .checkinAt(Instant.now())
                .method(AttendanceMethod.MANUAL)
                .verified(false)
                .nonce(nonce)
                .build());

        // ĐÂY LÀ BẢN GHI NHẬT KÝ QUAN TRỌNG NHẤT CỦA CẢ HỆ THỐNG.
        //
        // Điểm danh tay là cửa duy nhất mà dữ liệu vào hệ thống KHÔNG có gì máy móc chứng
        // minh — chính là "vấn đề oracle" ở dòng cuối bảng threat model. Blockchain sẽ bảo
        // toàn vĩnh viễn cả bản ghi sai nếu cán bộ nhập sai từ đầu.
        //
        // Không ngăn được, nhưng ghi lại được AI đã nhập, LÚC NÀO, cho SINH VIÊN NÀO — và
        // mắt xích làm việc sửa lại về sau trở nên phát hiện được. Đó đúng là mức mà đề tài
        // tuyên bố: không giải quyết bài toán oracle, mà ĐO ĐƯỢC và TRUY ĐƯỢC chất lượng
        // dữ liệu đầu vào (PROJECT.md §10).
        audit.record("ATTENDANCE_MANUAL", "attendances", a.getId(), actorId,
                null,
                AuditJson.of(
                        "eventId", eventId,
                        "studentId", studentId,
                        "method", "MANUAL",
                        "verified", false));

        return a;
    }
}
