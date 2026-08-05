package vn.ptit.drl.attendance;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.drl.event.Event;
import vn.ptit.drl.identity.Student;

@Entity
@Table(name = "attendances")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "checkin_at", nullable = false)
    private Instant checkinAt;

    @Column(name = "checkout_at")
    private Instant checkoutAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AttendanceMethod method;

    @Column(name = "device_fp", length = 128)
    private String deviceFp;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    /** epochSecond / qrSlotSeconds tại thời điểm quét. Null nếu điểm danh thủ công. */
    @Column(name = "qr_slot")
    private Long qrSlot;

    /**
     * True khi <b>máy</b> xác minh được sự có mặt tại thời điểm điểm danh, không phải chỉ
     * "có chữ ký hợp lệ".
     *
     * <p>Phân biệt này là thứ làm cho chỉ số chất lượng dữ liệu của đề tài có nghĩa
     * (PROJECT.md §10). Bốn trường hợp:
     *
     * <pre>
     *   QR_SCAN / OFFLINE_SYNC : true  — token gắn SỰ KIỆN, đổi mỗi 10s, quét bằng máy đã duyệt
     *   QR_SHOW, token còn tươi: true  — token gắn SINH VIÊN, chứng minh vừa đăng nhập vài giây trước
     *   QR_SHOW, token đã cũ   : false — chỉ mắt cán bộ chứng minh sự có mặt
     *   MANUAL                 : false — vấn đề oracle, không có gì chứng minh
     * </pre>
     *
     * <p>Đếm được bao nhiêu phần trăm bản ghi là {@code true} chính là đóng góp mà đề tài
     * tuyên bố, nên đừng nới định nghĩa này ra cho đẹp số.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean verified = false;

    /**
     * CẢNH BÁO MỀM, không chặn check-in. GPS trong nhà rất kém chính xác nên đây là
     * tín hiệu phụ để đánh dấu bản ghi cần xem lại. Null khi không có toạ độ.
     */
    @Column(name = "geofence_ok")
    private Boolean geofenceOk;

    /**
     * 16 byte ngẫu nhiên, sinh lúc tạo bản ghi. Thiếu nonce thì leaf_hash vét cạn được
     * (MSSV × eventId × thời gian ≈ 10⁸ tổ hợp) và proof của sinh viên này sẽ để lộ
     * bản ghi của sinh viên khác. Xem PROJECT.md §2.3.
     */
    // BINARY(n) chứ không VARBINARY: độ dài cố định. Thiếu @JdbcTypeCode thì
    // ddl-auto=validate báo lệch kiểu (Hibernate mặc định map byte[] -> VARBINARY).
    @Column(nullable = false, length = 16)
    @JdbcTypeCode(SqlTypes.BINARY)
    private byte[] nonce;

    /** Do job neo điền ở tuần 4. Null nghĩa là bản ghi chưa được neo. */
    @Column(name = "leaf_hash", length = 32)
    @JdbcTypeCode(SqlTypes.BINARY)
    private byte[] leafHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
