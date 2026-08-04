package vn.ptit.drl.org;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import vn.ptit.drl.common.web.BusinessException;
import vn.ptit.drl.common.web.NotFoundException;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Tổ chức cấp phát: trường, khoa, đoàn, CLB…")
public class OrganizationController {

    private final OrganizationRepository repository;

    public record OrgRequest(
            @NotBlank String name,
            @NotNull OrgType type,
            Long parentId,
            @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "Địa chỉ ví phải đúng định dạng 0x + 40 hex")
            String issuerAddress) {}

    public record OrgResponse(Long id, String name, OrgType type, Long parentId,
                              String issuerAddress, Instant onChainRegisteredAt) {

        static OrgResponse of(Organization o) {
            return new OrgResponse(o.getId(), o.getName(), o.getType(),
                    o.getParent() == null ? null : o.getParent().getId(),
                    o.getIssuerAddress(), o.getOnChainRegisteredAt());
        }
    }

    @GetMapping
    @Operation(summary = "Danh sách tổ chức, lọc theo loại")
    @Transactional(readOnly = true)
    public List<OrgResponse> list(@RequestParam(required = false) OrgType type) {
        List<Organization> found = (type == null) ? repository.findAll() : repository.findByType(type);
        return found.stream().map(OrgResponse::of).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public OrgResponse get(@PathVariable Long id) {
        return OrgResponse.of(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("tổ chức", id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tạo tổ chức mới")
    @Transactional
    public OrgResponse create(@Valid @RequestBody OrgRequest req) {
        Organization org = Organization.builder()
                .name(req.name())
                .type(req.type())
                .parent(resolveParent(req.parentId(), null))
                .issuerAddress(req.issuerAddress())
                .build();
        return OrgResponse.of(repository.save(org));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrgResponse update(@PathVariable Long id, @Valid @RequestBody OrgRequest req) {
        Organization org = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("tổ chức", id));
        org.setName(req.name());
        org.setType(req.type());
        org.setParent(resolveParent(req.parentId(), id));
        org.setIssuerAddress(req.issuerAddress());
        return OrgResponse.of(repository.save(org));
    }

    private Organization resolveParent(Long parentId, Long selfId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(selfId)) {
            throw new BusinessException("Tổ chức không thể là cha của chính nó");
        }
        return repository.findById(parentId)
                .orElseThrow(() -> new NotFoundException("tổ chức cha", parentId));
    }
}
