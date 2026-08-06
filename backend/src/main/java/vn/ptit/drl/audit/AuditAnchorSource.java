package vn.ptit.drl.audit;

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
 * Nguồn bản ghi nhật ký cho job neo — miền {@code AUDIT}.
 *
 * <p><b>Đây là mắt xích cuối cùng của luận điểm 1.</b> Chuỗi băm một mình chỉ chứng minh tính
 * nhất quán nội bộ: kẻ tấn công có toàn quyền CSDL sửa một bản ghi rồi tính lại cả chuỗi từ
 * đó về sau, và {@code AuditService.verifyChain()} lại xanh. Cái chặn việc đó là <b>root đã
 * nằm trên chuỗi công khai</b> — không tính lại được nữa.
 *
 * <p>Nói cách khác: chuỗi băm làm việc sửa <i>tốn kém</i>; việc neo làm nó <i>bất khả thi</i>
 * đối với khoảng thời gian đã neo. Đó là phát biểu đúng mức để đưa vào báo cáo.
 *
 * <h2>Mốc chốt: ghi xong là chốt</h2>
 *
 * <p>Nhật ký chỉ ghi thêm, và mọi cột đều {@code updatable = false}, nên không có gì để đợi —
 * khác {@code attendance} phải đợi {@code events.end_at}.
 *
 * <h2>Neo càng dày càng chặt</h2>
 *
 * <p>Khoảng thời gian giữa hai lần neo chính là <b>cửa sổ mà việc sửa hồi tố vẫn giấu
 * được</b>. Job chạy 02:00 hằng đêm nên cửa sổ đó là 24 giờ. Thu hẹp nó chỉ tốn thêm giao
 * dịch chứ không tốn thiết kế — chi phí neo không phụ thuộc số bản ghi trong lô
 * ({@code docs/measurements.md} §11.1), nên neo mỗi giờ đắt gấp 24 lần mà vẫn là con số nhỏ.
 * <b>Đây là một cái núm đánh đổi định lượng được, nên nêu trong báo cáo.</b>
 */
@Component
@RequiredArgsConstructor
public class AuditAnchorSource implements AnchorSource {

  private final AuditLogRepository repository;

  @Override
  public AnchorDomain domain() {
    return AnchorDomain.AUDIT;
  }

  @Override
  public String sourceTable() {
    return "audit_logs";
  }

  @Override
  @Transactional(readOnly = true)
  public List<Item> pending(int limit) {
    List<AuditLog> rows = repository.findPendingAnchor(PageRequest.of(0, limit));

    List<Item> items = new ArrayList<>(rows.size());
    for (AuditLog e : rows) {
      // Không neo một chuỗi đang hỏng. Neo mắt xích sai nghĩa là đóng dấu vĩnh viễn lên một
      // bằng chứng đã vô hiệu — và AnchorRegistry không cho neo lại để sửa.
      byte[] tinhLai = AuditHasher.chainHash(e);
      if (!java.util.Arrays.equals(tinhLai, e.getHash())) {
        throw new IllegalStateException(
            "Bản ghi nhật ký #" + e.getId() + " có hash không khớp nội dung — chuỗi băm đã bị"
                + " phá. KHÔNG neo lô AUDIT nào cho tới khi việc này được điều tra."
                + " Chạy AuditService.verifyChain() để xem toàn bộ.");
      }
      items.add(new Item(e.getId(), AuditPayload.of(e)));
    }
    return items;
  }

  @Override
  @Transactional
  public void saveLeafHashes(Map<Long, byte[]> leafHashBySourceId) {
    for (Map.Entry<Long, byte[]> e : leafHashBySourceId.entrySet()) {
      repository.updateLeafHash(e.getKey(), e.getValue());
    }
  }
}
