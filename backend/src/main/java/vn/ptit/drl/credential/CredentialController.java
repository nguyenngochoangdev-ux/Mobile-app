package vn.ptit.drl.credential;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import vn.ptit.drl.common.web.BusinessException;
import vn.ptit.drl.common.web.NotFoundException;
import vn.ptit.drl.identity.StudentRepository;
import vn.ptit.drl.identity.jwt.AuthPrincipal;
import vn.ptit.drl.org.OrganizationRepository;

@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
@Tag(name = "Credential", description = "Cấp và tra cứu credential có chữ ký của tổ chức")
public class CredentialController {

  private static final HexFormat HEX = HexFormat.of();

  private final CredentialService service;
  private final CredentialBundleService bundleService;
  private final CredentialRevocationService revocationService;
  private final CredentialRepository repository;
  private final StudentRepository studentRepository;
  private final OrganizationRepository organizationRepository;

  // ---------------------------------------------------------------- cấp

  public record IssueRequest(
      @NotNull Long studentId,
      @NotNull Long issuerOrgId,
      @NotBlank @Size(max = 16) @Pattern(regexp = "^\\d{4}-[12]$",
          message = "Học kỳ phải có dạng YYYY-1 hoặc YYYY-2, ví dụ 2026-1")
      String semester,
      @NotNull @Min(0) Integer activityCount,
      @NotNull @Min(0) Integer totalPoints,
      Instant expiresAt) {}

  /**
   * Thông tin trả về sau khi cấp.
   *
   * <p>Trả cả {@code leafHash} và {@code signature} để cán bộ đối chiếu được ngay, và vì đây
   * cũng là hai thứ sẽ đi vào bundle của sinh viên (tuần 4, phần còn lại).
   */
  public record CredentialResponse(
      Long id, String studentCode, String studentName, String type, String semester,
      Integer activityCount, Integer totalPoints, String issuerAddress, Instant issuedAt,
      Instant expiresAt, Long statusListIndex, String leafHash, String signature,
      String payloadJson, boolean revoked, Instant revokedAt) {}

  @PostMapping
  @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(operationId = "issueCredential", summary = "Cấp credential cho một sinh viên",
      description = """
          Chụp ảnh MSSV/họ tên/địa chỉ ví, cấp status_list_index ngẫu nhiên, dựng payload
          chuẩn tắc, tính leaf hash và ký bằng khóa của tổ chức. Credential được neo ở lần
          chạy job neo tiếp theo.

          KHÔNG có endpoint sửa credential — đó là chủ ý. Credential là phát biểu đã ký;
          sửa nó làm mọi Merkle proof fail vĩnh viễn. Muốn đổi nội dung thì thu hồi rồi cấp
          lại, đúng mô hình W3C Verifiable Credentials.
          """)
  @Transactional
  public CredentialResponse issue(@Valid @RequestBody IssueRequest req,
                                  @AuthenticationPrincipal AuthPrincipal principal) {
    var student = studentRepository.findById(req.studentId())
        .orElseThrow(() -> new NotFoundException("Không thấy sinh viên " + req.studentId()));
    var org = organizationRepository.findById(req.issuerOrgId())
        .orElseThrow(() -> new NotFoundException("Không thấy tổ chức " + req.issuerOrgId()));

    // Một sinh viên chỉ có một credential HOAT_DONG cho mỗi học kỳ. Chặn ở đây thay vì để
    // trùng lặp lặng lẽ đi vào lô neo: hai credential nội dung giống hệt nhau vẫn cho ra hai
    // leaf khác nhau (nonce riêng), nên cây Merkle KHÔNG bắt được chuyện này.
    repository.findByStudentIdAndSemesterAndType(
            req.studentId(), req.semester(), CredentialType.HOAT_DONG)
        .ifPresent(c -> {
          throw new BusinessException(
              "Sinh viên này đã có credential HOAT_DONG cho học kỳ " + req.semester()
                  + " (id " + c.getId() + "). Thu hồi bản cũ trước khi cấp bản mới.");
        });

    return toResponse(service.issue(new CredentialService.Request(
        student, org, req.semester(), req.activityCount(), req.totalPoints(),
        req.expiresAt(), principal.userId())));
  }

  // ---------------------------------------------------------------- tra cứu

  @GetMapping("/me")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(operationId = "myCredentials", summary = "Credential của chính mình")
  @Transactional(readOnly = true)
  public List<CredentialResponse> mine(@AuthenticationPrincipal AuthPrincipal principal) {
    return repository.findByStudentIdOrderByIssuedAtDesc(principal.studentId())
        .stream().map(CredentialController::toResponse).toList();
  }

  @GetMapping("/student/{studentId}")
  @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
  @Operation(operationId = "credentialsOfStudent", summary = "Credential của một sinh viên")
  @Transactional(readOnly = true)
  public List<CredentialResponse> ofStudent(@PathVariable Long studentId) {
    return repository.findByStudentIdOrderByIssuedAtDesc(studentId)
        .stream().map(CredentialController::toResponse).toList();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
  @Operation(operationId = "getCredential", summary = "Chi tiết một credential")
  @Transactional(readOnly = true)
  public CredentialResponse one(@PathVariable Long id,
                                @AuthenticationPrincipal AuthPrincipal principal) {
    Credential c = repository.findById(id)
        .orElseThrow(() -> new NotFoundException("Không thấy credential " + id));

    // Sinh viên chỉ xem được credential của mình. Không phải bí mật lớn — nội dung sẽ nằm
    // trong bundle mà chính họ đưa cho nhà tuyển dụng — nhưng để lộ credential của người
    // khác qua id tuần tự thì là một kiểu duyệt danh sách toàn trường.
    if ("STUDENT".equals(principal.role())
        && !c.getStudent().getId().equals(principal.studentId())) {
      throw new NotFoundException("Không thấy credential " + id);
    }
    return toResponse(c);
  }

  // ---------------------------------------------------------------- thu hồi

  public record RevokeRequest(@Size(max = 255) String reason, Boolean revoked) {}

  @PostMapping("/{id}/revoke")
  @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
  @Operation(operationId = "revokeCredential", summary = "Thu hồi credential (lật bit trên StatusList)",
      description = """
          Gửi giao dịch `setRevoked` lên chuỗi TRƯỚC, ghi CSDL SAU — ngược thứ tự với job neo,
          và đó là chủ ý: nguồn sự thật về thu hồi là bit trên chuỗi, vì đó là thứ duy nhất
          verifier đọc. Ghi CSDL trước rồi giao dịch hỏng sẽ làm trang quản trị báo "đã thu
          hồi" trong khi nhà tuyển dụng chạy verifier vẫn thấy credential còn hiệu lực.

          Đặt `revoked = false` để BỎ thu hồi. Thao tác này đảo ngược được (khác `anchor`),
          nhưng lịch sử thì không: mỗi lần lật để lại một sự kiện `StatusChanged` vĩnh viễn.

          Cần `drl.anchor.enabled=true`. Cố ý KHÔNG có chế độ thu hồi cục bộ.
          """)
  @Transactional
  public CredentialRevocationService.Result revoke(
      @PathVariable Long id, @Valid @RequestBody(required = false) RevokeRequest req,
      @AuthenticationPrincipal AuthPrincipal principal) {

    boolean revoked = req == null || req.revoked() == null || req.revoked();
    return revocationService.setRevoked(
        id, revoked, req == null ? null : req.reason(), principal.userId());
  }

  // ---------------------------------------------------------------- bundle

  @GetMapping("/{id}/bundle")
  @PreAuthorize("hasAnyRole('STUDENT','STAFF','ADMIN')")
  @Operation(operationId = "credentialBundle", summary = "Xuất bundle xác minh độc lập",
      description = """
          Tệp JSON tự chứa: payload đã ký, chữ ký, Merkle proof, batchId và địa chỉ contract.
          Xác minh bằng `cd verifier && node scripts/verify-bundle.mjs <tệp>` — script đó chỉ
          dùng ethers + merkletreejs và KHÔNG gọi backend một dòng nào.

          Địa chỉ contract trong mục `chain` là THÔNG TIN, không phải cấu hình: verifier giữ
          danh sách địa chỉ tin cậy của riêng nó và từ chối bundle khai địa chỉ khác. Tin
          địa chỉ trong bundle là cách phá hệ thống rẻ nhất — kẻ tấn công chỉ cần deploy một
          contract trả về root do mình chọn.

          Chỉ xuất được sau khi credential đã neo và lô đã lên chuỗi.
          """)
  @Transactional(readOnly = true)
  public CredentialBundleService.Bundle bundle(
      @PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {

    Credential c = repository.findById(id)
        .orElseThrow(() -> new NotFoundException("Không thấy credential " + id));

    if ("STUDENT".equals(principal.role())
        && !c.getStudent().getId().equals(principal.studentId())) {
      throw new NotFoundException("Không thấy credential " + id);
    }
    return bundleService.build(id);
  }

  // ---------------------------------------------------------------- ánh xạ

  private static CredentialResponse toResponse(Credential c) {
    return new CredentialResponse(
        c.getId(), c.getStudentCode(), c.getStudentName(), c.getType().name(),
        c.getSemester(), c.getActivityCount(), c.getTotalPoints(), c.getIssuerAddress(),
        c.getIssuedAt(), c.getExpiresAt(), c.getStatusListIndex(),
        "0x" + HEX.formatHex(c.getLeafHash()),
        "0x" + HEX.formatHex(c.getSignature()),
        c.getPayloadJson(),
        c.getRevokedAt() != null, c.getRevokedAt());
  }
}
