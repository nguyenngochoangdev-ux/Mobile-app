package vn.ptit.drl.identity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thiết bị đã đăng ký của sinh viên.
 * <p>
 * Thiếu bảng này thì toàn bộ cơ chế QR động vô nghĩa: sinh viên chỉ cần đưa tài khoản
 * cho bạn quét hộ. Đổi thiết bị phải qua duyệt của cán bộ và có ghi nhật ký.
 */
@Entity
@Table(name = "student_devices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudentDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "device_fp", nullable = false, length = 128)
    private String deviceFp;

    @Column(length = 128)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private DeviceStatus status = DeviceStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
