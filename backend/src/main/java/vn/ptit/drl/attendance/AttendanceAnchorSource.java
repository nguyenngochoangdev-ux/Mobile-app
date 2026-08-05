package vn.ptit.drl.attendance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import vn.ptit.drl.anchor.AnchorDomain;
import vn.ptit.drl.anchor.AnchorSource;

/**
 * Nguồn bản ghi điểm danh cho job neo — miền {@code ATTEND}.
 *
 * <p>Chiều phụ thuộc: {@code attendance} → {@code anchor}. Ngược lại thì vi phạm ranh giới
 * PROJECT.md §5; đó là lý do {@link AnchorSource} khai ở module {@code anchor} còn lớp này
 * hiện thực nó.
 *
 * <h2>Chỉ neo bản ghi của sự kiện ĐÃ KẾT THÚC</h2>
 *
 * <p>Payload có {@code checkOutAt} ({@link AttendancePayload}), mà sinh viên còn check-out
 * được chừng nào sự kiện chưa xong. Neo sớm là leaf lạc hậu <b>vĩnh viễn</b> —
 * {@code AnchorRegistry} không cho neo lại, nên không có cách nào sửa. Mốc chốt vì thế là
 * {@code events.end_at} đã qua.
 *
 * <p>Job chạy 02:00 hằng đêm nên sự kiện trong ngày đều đã kết thúc từ lâu; điều kiện này
 * gần như không làm chậm gì, chỉ chặn đúng trường hợp sự kiện kéo dài qua đêm.
 */
@Component
@RequiredArgsConstructor
public class AttendanceAnchorSource implements AnchorSource {

  private final AttendanceRepository repository;

  @Override
  public AnchorDomain domain() {
    return AnchorDomain.ATTEND;
  }

  @Override
  public String sourceTable() {
    return "attendances";
  }

  @Override
  @Transactional(readOnly = true)
  public List<Item> pending(int limit) {
    List<Attendance> rows =
        repository.findPendingAnchor(Instant.now(), PageRequest.of(0, limit));

    List<Item> items = new ArrayList<>(rows.size());
    for (Attendance a : rows) {
      items.add(new Item(a.getId(), AttendancePayload.of(a)));
    }
    return items;
  }

  @Override
  @Transactional
  public void saveLeafHashes(Map<Long, byte[]> leafHashBySourceId) {
    for (Map.Entry<Long, byte[]> e : leafHashBySourceId.entrySet()) {
      repository.findById(e.getKey()).ifPresent(a -> a.setLeafHash(e.getValue()));
    }
  }
}
