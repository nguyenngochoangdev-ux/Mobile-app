package vn.ptit.drl.event;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;

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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import vn.ptit.drl.common.web.BusinessException;
import vn.ptit.drl.common.web.NotFoundException;
import vn.ptit.drl.org.OrganizationRepository;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Sự kiện và hoạt động")
public class EventController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EventRepository repository;
    private final OrganizationRepository orgRepository;

    public record EventRequest(
            @NotNull Long orgId,
            @NotBlank String title,
            @NotBlank String type,
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            String location,
            BigDecimal lat,
            BigDecimal lng,
            Integer radiusM,
            Integer capacity,
            @Pattern(regexp = "^C[1-5]$", message = "criteriaCode phải là C1..C5")
            String criteriaCode,
            Integer points) {}

    /**
     * Không bao giờ chứa {@code secretKey}. Lộ secret của một sự kiện là mất toàn bộ
     * cơ chế QR động của sự kiện đó — ai cũng sinh được token hợp lệ.
     */
    public record EventResponse(Long id, Long orgId, String title, String type,
                                Instant startAt, Instant endAt, String location,
                                BigDecimal lat, BigDecimal lng, Integer radiusM,
                                Integer capacity, String criteriaCode, Integer points,
                                EventStatus status) {

        static EventResponse of(Event e) {
            return new EventResponse(e.getId(), e.getOrg().getId(), e.getTitle(), e.getType(),
                    e.getStartAt(), e.getEndAt(), e.getLocation(), e.getLat(), e.getLng(),
                    e.getRadiusM(), e.getCapacity(), e.getCriteriaCode(), e.getPoints(),
                    e.getStatus());
        }
    }

    @GetMapping
    @Operation(summary = "Danh sách sự kiện, phân trang")
    @Transactional(readOnly = true)
    public Page<EventResponse> list(@RequestParam(required = false) EventStatus status,
                                    @RequestParam(required = false) Long orgId,
                                    Pageable pageable) {
        Page<Event> page;
        if (status != null) {
            page = repository.findByStatus(status, pageable);
        } else if (orgId != null) {
            page = repository.findByOrgId(orgId, pageable);
        } else {
            page = repository.findAll(pageable);
        }
        return page.map(EventResponse::of);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public EventResponse get(@PathVariable Long id) {
        return EventResponse.of(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("sự kiện", id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Tạo sự kiện. Secret HMAC sinh phía server, không nhận từ client.")
    @Transactional
    public EventResponse create(@Valid @RequestBody EventRequest req) {
        validateTime(req);

        // Seed HMAC RIÊNG cho từng sự kiện — không dùng chung một secret toàn hệ thống.
        // Lộ một secret chỉ ảnh hưởng một sự kiện.
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);

        Event event = Event.builder()
                .org(orgRepository.findById(req.orgId())
                        .orElseThrow(() -> new NotFoundException("tổ chức", req.orgId())))
                .title(req.title())
                .type(req.type())
                .startAt(req.startAt())
                .endAt(req.endAt())
                .location(req.location())
                .lat(req.lat())
                .lng(req.lng())
                .radiusM(req.radiusM() == null ? 100 : req.radiusM())
                .capacity(req.capacity())
                .secretKey(secret)
                .criteriaCode(req.criteriaCode())
                .points(req.points() == null ? 0 : req.points())
                .status(EventStatus.DRAFT)
                .build();

        return EventResponse.of(repository.save(event));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Transactional
    public EventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest req) {
        validateTime(req);
        Event event = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("sự kiện", id));

        // secretKey KHÔNG đổi khi sửa sự kiện: đổi giữa chừng làm mọi QR đang hiển thị
        // trên màn hình presenter mất hiệu lực ngay lập tức.
        event.setTitle(req.title());
        event.setType(req.type());
        event.setStartAt(req.startAt());
        event.setEndAt(req.endAt());
        event.setLocation(req.location());
        event.setLat(req.lat());
        event.setLng(req.lng());
        event.setRadiusM(req.radiusM());
        event.setCapacity(req.capacity());
        event.setCriteriaCode(req.criteriaCode());
        event.setPoints(req.points());
        return EventResponse.of(repository.save(event));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(summary = "Đổi trạng thái sự kiện")
    @Transactional
    public EventResponse changeStatus(@PathVariable Long id, @RequestParam EventStatus status) {
        Event event = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("sự kiện", id));
        event.setStatus(status);
        return EventResponse.of(repository.save(event));
    }

    private void validateTime(EventRequest req) {
        if (req.endAt().isBefore(req.startAt())) {
            throw new BusinessException("Thời gian kết thúc phải sau thời gian bắt đầu");
        }
    }
}
