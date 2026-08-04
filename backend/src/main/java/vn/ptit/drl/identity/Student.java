package vn.ptit.drl.identity;

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
@Table(name = "students")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String mssv;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "class_code", length = 32)
    private String classCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private Organization faculty;

    @Column(length = 16)
    private String cohort;

    /**
     * did:key sinh từ khóa issuer của tổ chức — KHÔNG phải khóa riêng của sinh viên.
     * HD wallet per-student đã bị cắt khỏi phạm vi (docs/scope.md): trong chuẩn W3C VC,
     * issuer là tổ chức và sinh viên là subject, không cần khóa riêng.
     */
    @Column(length = 255)
    private String did;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
