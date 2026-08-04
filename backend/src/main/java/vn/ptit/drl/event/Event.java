package vn.ptit.drl.event;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.ptit.drl.org.Organization;

@Entity
@Table(name = "events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization org;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    private String location;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(name = "radius_m")
    private Integer radiusM;

    private Integer capacity;

    /**
     * Seed HMAC RIÊNG cho từng sự kiện — không dùng chung một secret toàn hệ thống.
     * Lộ một secret chỉ ảnh hưởng một sự kiện.
     * <p>
     * Không bao giờ trả trường này ra API. Chỉ màn hình presenter (đã xác thực) mới
     * nhận được token sinh từ nó, và nhận token chứ không nhận secret.
     */
    @Column(name = "secret_key", nullable = false, length = 32)
    private byte[] secretKey;

    /** Tiêu chí rèn luyện C1..C5 mà sự kiện này đóng góp điểm. */
    @Column(name = "criteria_code", length = 8)
    private String criteriaCode;

    @Builder.Default
    private Integer points = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private EventStatus status = EventStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
