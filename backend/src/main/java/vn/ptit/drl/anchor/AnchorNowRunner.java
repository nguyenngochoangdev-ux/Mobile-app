package vn.ptit.drl.anchor;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Chạy job neo <b>ngay lập tức</b> rồi thoát, thay vì đợi lịch 02:00.
 *
 * <pre>
 *   .\scripts\anchor-now.ps1
 * </pre>
 *
 * <p>Có hai lý do cần nó, đều thật:
 *
 * <ul>
 *   <li><b>Buổi nghiệm thu.</b> Hội đồng không đợi đến 2 giờ sáng. Luồng demo end-to-end
 *       (điểm danh → neo → xác minh độc lập) cần neo được theo yêu cầu.
 *   <li><b>Lần neo đầu tiên.</b> Đóng cổng kiểm soát cuối tuần 3 cần một giao dịch neo thật
 *       trên explorer, không phải một job chờ đến đêm.
 * </ul>
 *
 * <p>Dùng profile chứ không phải endpoint HTTP: neo là thao tác <b>không thể hoàn tác</b>, và
 * một endpoint là thêm một bề mặt để bấm nhầm. Profile bắt phải cố ý gõ ra.
 *
 * <p>Cùng lối với profile {@code seed} mà {@code scripts/reset-db.ps1} đang dùng.
 */
@Component
@Profile("anchor-now")
@RequiredArgsConstructor
@Slf4j
public class AnchorNowRunner implements ApplicationRunner {

  private final AnchorJob job;

  @Override
  public void run(ApplicationArguments args) {
    log.info("=== Neo ngay (profile anchor-now) ===");

    List<AnchorJob.Result> results = job.runAll();

    if (results.isEmpty()) {
      log.info("Khong co ban ghi nao cho neo.");
      log.info("Nho: chi ban ghi cua su kien DA KET THUC moi duoc neo"
          + " (xem AttendanceAnchorSource).");
      return;
    }

    log.info("Da neo {} lo:", results.size());
    for (AnchorJob.Result r : results) {
      log.info("  {} · lo {} ({}) · {} la · gas {}",
          r.domain(), r.batchId(), AnchorBatchId.describe(r.batchId()),
          r.leafCount(), r.gasUsed());
      log.info("     https://amoy.polygonscan.com/tx/{}", r.txHash());
    }
    log.info("Ghi so lieu vao docs/measurements.md — goi /measurements.");
  }
}
