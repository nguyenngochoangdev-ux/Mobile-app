package vn.ptit.drl.org;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "organizations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrgType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Organization parent;

    /**
     * Địa chỉ ví đã đăng ký trong IssuerRegistry. Null cho tới khi đồng bộ lên chuỗi.
     * <p>
     * CHAR(42) chứ không VARCHAR: địa chỉ Ethereum luôn đúng 42 ký tự ("0x" + 40 hex).
     * Cần {@code @JdbcTypeCode(CHAR)} nếu không {@code ddl-auto=validate} sẽ báo lệch kiểu.
     */
    @Column(name = "issuer_address", length = 42)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String issuerAddress;

    @Column(name = "on_chain_registered_at")
    private Instant onChainRegisteredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
