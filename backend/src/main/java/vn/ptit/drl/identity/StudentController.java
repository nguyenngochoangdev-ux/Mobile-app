package vn.ptit.drl.identity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import vn.ptit.drl.common.web.BusinessException;
import vn.ptit.drl.common.web.NotFoundException;
import vn.ptit.drl.org.OrganizationRepository;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Tag(name = "Students", description = "Hồ sơ sinh viên")
public class StudentController {

    private final StudentRepository repository;
    private final OrganizationRepository orgRepository;

    public record StudentRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{6,32}$", message = "MSSV chỉ gồm chữ và số, 6-32 ký tự")
            String mssv,
            @NotBlank String fullName,
            String classCode,
            Long facultyId,
            String cohort) {}

    public record StudentResponse(Long id, String mssv, String fullName, String classCode,
                                  Long facultyId, String facultyName, String cohort, String did) {

        static StudentResponse of(Student s) {
            return new StudentResponse(s.getId(), s.getMssv(), s.getFullName(), s.getClassCode(),
                    s.getFaculty() == null ? null : s.getFaculty().getId(),
                    s.getFaculty() == null ? null : s.getFaculty().getName(),
                    s.getCohort(), s.getDid());
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Tìm kiếm sinh viên theo tên/MSSV, lọc theo lớp và khoa")
    @Transactional(readOnly = true)
    public Page<StudentResponse> list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) String classCode,
                                      @RequestParam(required = false) Long facultyId,
                                      Pageable pageable) {
        String trimmed = (q == null || q.isBlank()) ? null : q.trim();
        return repository.search(trimmed, classCode, facultyId, pageable)
                .map(StudentResponse::of);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Transactional(readOnly = true)
    public StudentResponse get(@PathVariable Long id) {
        return StudentResponse.of(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("sinh viên", id)));
    }

    @GetMapping("/mssv/{mssv}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Tra cứu theo MSSV — dùng khi cán bộ điểm danh thủ công")
    @Transactional(readOnly = true)
    public StudentResponse getByMssv(@PathVariable String mssv) {
        return StudentResponse.of(repository.findByMssv(mssv)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sinh viên MSSV " + mssv)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Transactional
    public StudentResponse create(@Valid @RequestBody StudentRequest req) {
        if (repository.existsByMssv(req.mssv())) {
            throw new BusinessException("MSSV " + req.mssv() + " đã tồn tại");
        }
        Student s = Student.builder()
                .mssv(req.mssv())
                .fullName(req.fullName())
                .classCode(req.classCode())
                .cohort(req.cohort())
                .faculty(req.facultyId() == null ? null : orgRepository.findById(req.facultyId())
                        .orElseThrow(() -> new NotFoundException("khoa", req.facultyId())))
                .build();
        return StudentResponse.of(repository.save(s));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Transactional
    public StudentResponse update(@PathVariable Long id, @Valid @RequestBody StudentRequest req) {
        Student s = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("sinh viên", id));

        // MSSV đổi được nhưng phải không trùng — MSSV nhập sai lúc tạo là chuyện có thật.
        if (!s.getMssv().equals(req.mssv()) && repository.existsByMssv(req.mssv())) {
            throw new BusinessException("MSSV " + req.mssv() + " đã tồn tại");
        }
        s.setMssv(req.mssv());
        s.setFullName(req.fullName());
        s.setClassCode(req.classCode());
        s.setCohort(req.cohort());
        s.setFaculty(req.facultyId() == null ? null : orgRepository.findById(req.facultyId())
                .orElseThrow(() -> new NotFoundException("khoa", req.facultyId())));
        return StudentResponse.of(repository.save(s));
    }
}
