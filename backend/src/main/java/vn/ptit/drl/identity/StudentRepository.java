package vn.ptit.drl.identity;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByMssv(String mssv);

    boolean existsByMssv(String mssv);

    /**
     * Tìm kiếm gộp. Tham số null nghĩa là không lọc theo tiêu chí đó, nhờ vậy một
     * endpoint phục vụ được cả danh sách toàn trường lẫn lọc theo lớp/khoa.
     */
    @Query("""
            SELECT s FROM Student s
            WHERE (:q IS NULL OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(s.mssv)     LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:classCode IS NULL OR s.classCode = :classCode)
              AND (:facultyId IS NULL OR s.faculty.id = :facultyId)
            """)
    Page<Student> search(@Param("q") String q,
                         @Param("classCode") String classCode,
                         @Param("facultyId") Long facultyId,
                         Pageable pageable);
}
