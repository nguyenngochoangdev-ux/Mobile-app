package vn.ptit.drl.identity;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByMssv(String mssv);
    boolean existsByMssv(String mssv);
    Page<Student> findByClassCode(String classCode, Pageable pageable);
}
