package vn.ptit.drl.scoring;

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
 * Nguồn bộ quy tắc cho job neo — miền {@code RULESET}.
 *
 * <p>Miền nhỏ nhất trong năm miền: mỗi học kỳ vài bản ghi, không phải vài nghìn. Nhưng bỏ nó
 * đi thì <b>miền {@code SCORE} mất một nửa ý nghĩa</b> — một điểm số đã neo mà bộ quy tắc
 * giải thích nó thì sửa được là một bằng chứng nửa vời.
 *
 * <p>Chi phí neo không phụ thuộc số lá ({@code docs/measurements.md} §11.1), nên một lô một
 * lá tốn đúng bằng một lô năm nghìn lá. Miền này vì thế là ví dụ rõ nhất cho việc <b>gộp lô
 * chỉ có nghĩa khi có gì để gộp</b>; ở đây không có, và đó không phải vấn đề.
 */
@Component
@RequiredArgsConstructor
public class RulesetAnchorSource implements AnchorSource {

  private final RulesetRepository repository;

  @Override
  public AnchorDomain domain() {
    return AnchorDomain.RULESET;
  }

  @Override
  public String sourceTable() {
    return "rulesets";
  }

  @Override
  @Transactional(readOnly = true)
  public List<Item> pending(int limit) {
    List<Ruleset> rows = repository.findPendingAnchor(PageRequest.of(0, limit));

    List<Item> items = new ArrayList<>(rows.size());
    for (Ruleset r : rows) {
      // Kiểm lại byte trước khi neo: nếu nội dung tệp trong CSDL không còn băm ra đúng
      // `ruleset_hash` đã lưu thì có ai đó sửa thẳng bảng, và neo tiếp là đóng dấu vĩnh viễn
      // lên một mâu thuẫn.
      byte[] tinhLai = RulesetPayload.rulesetHash(r.getJsonBody());
      if (!java.util.Arrays.equals(tinhLai, r.getRulesetHash())) {
        throw new IllegalStateException(
            "Bộ quy tắc " + r.getVersion() + ": nội dung trong CSDL không khớp ruleset_hash"
                + " đã lưu. KHÔNG neo cho tới khi việc này được điều tra.");
      }

      items.add(new Item(r.getId(), RulesetPayload.of(
          r.getVersion(), r.getSemester(), r.getRulesetHash(),
          r.getEffectiveFrom(), r.getNonce())));
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
