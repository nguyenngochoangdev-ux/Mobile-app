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
 * Nguồn bản ghi điểm cho job neo — miền {@code SCORE}.
 *
 * <p>Mốc chốt: <b>điểm đã ghi là chốt</b>. Mọi cột trong payload khai {@code updatable =
 * false}, và chấm lại thì tạo lượt mới chứ không sửa lượt cũ — nên không có gì để đợi, khác
 * {@code attendance} phải đợi {@code events.end_at}.
 *
 * <p>Neo điểm mà không neo bộ quy tắc thì sửa quy chế sau khi công bố điểm là việc không ai
 * phát hiện được. Đó là lý do miền {@code RULESET} tồn tại song song — xem
 * {@link RulesetAnchorSource}. Hai miền phải cùng được neo thì phát biểu mới đầy đủ.
 */
@Component
@RequiredArgsConstructor
public class ScoreAnchorSource implements AnchorSource {

  private final ScoreRepository repository;
  private final ScoringService scoringService;

  @Override
  public AnchorDomain domain() {
    return AnchorDomain.SCORE;
  }

  @Override
  public String sourceTable() {
    return "scores";
  }

  @Override
  @Transactional(readOnly = true)
  public List<Item> pending(int limit) {
    List<Score> rows = repository.findPendingAnchor(PageRequest.of(0, limit));

    List<Item> items = new ArrayList<>(rows.size());
    for (Score s : rows) {
      Ruleset rs = s.getRun().getRuleset();

      items.add(new Item(s.getId(), ScorePayload.of(
          s.getStudent().getMssv(),
          s.getRun().getSemester(),
          rs.getVersion(),
          rs.getRulesetHash(),
          s.getRun().getRunAt(),
          Map.of("C1", s.getC1(), "C2", s.getC2(), "C3", s.getC3(),
              "C4", s.getC4(), "C5", s.getC5()),
          s.getTotal(),
          s.getClassification(),
          s.getEvidenceHash(),
          s.getNonce())));
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
