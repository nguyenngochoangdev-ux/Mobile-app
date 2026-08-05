package vn.ptit.drl.anchor;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Quy ước {@code batchId}: {@code YYYYMMDD} kèm số thứ tự trong ngày.
 *
 * <pre>
 *   2026080601  →  ngày 2026-08-06, lô thứ 1 trong ngày
 * </pre>
 *
 * <p><b>Chốt vĩnh viễn từ lần neo đầu tiên.</b> {@code AnchorRegistry} không cho ghi đè, nên
 * mỗi {@code (domain, batchId)} chỉ dùng được đúng một lần — đổi quy ước về sau nghĩa là bỏ
 * hẳn không gian chỉ số cũ.
 *
 * <h2>Vì sao không đánh số tuần tự 1, 2, 3…</h2>
 *
 * <p>Tuần tự thì muốn biết lô nào neo ngày nào phải tra bảng {@code anchor_batches} — tức là
 * <b>phụ thuộc vào CSDL của trường</b>, đúng thứ luận điểm 2 (xác minh độc lập sau khi sinh
 * viên rời trường) muốn tránh. Với quy ước này, người cầm bundle đọc luôn được ngày neo từ
 * chính con số, và verifier chỉ cần một {@code eth_call}.
 *
 * <p>Số thứ tự trong ngày để neo lại được khi lần đầu hỏng giữa chừng: lô 01 đã gửi lên chuỗi
 * thì không đụng vào nữa, lô 02 mang phần còn lại.
 *
 * <h2>Không giấu thông tin gì</h2>
 *
 * <p>Khác {@code status_list_index} (cấp ngẫu nhiên để không lộ thứ tự cấp phát), {@code
 * batchId} là dữ liệu <b>cấp lô</b>, không gắn với sinh viên nào. Lộ ngày neo là điều mong
 * muốn — đó chính là bằng chứng tồn tại tại một thời điểm.
 */
public final class AnchorBatchId {

  /** Tối đa 99 lô một ngày một miền. Thực tế job chạy một lần mỗi đêm. */
  public static final int MAX_SEQ = 99;

  private AnchorBatchId() {}

  /** {@code (2026-08-06, 1)} → {@code 2026080601}. */
  public static long of(LocalDate date, int seq) {
    if (seq < 1 || seq > MAX_SEQ) {
      throw new IllegalArgumentException(
          "Số thứ tự lô phải trong [1, " + MAX_SEQ + "], nhận được: " + seq);
    }
    long ymd = date.getYear() * 10_000L + date.getMonthValue() * 100L + date.getDayOfMonth();
    return ymd * 100L + seq;
  }

  /** Lô đầu tiên của hôm nay, theo UTC — cùng múi giờ với mọi thời gian được neo. */
  public static long today(int seq) {
    return of(LocalDate.now(ZoneOffset.UTC), seq);
  }

  public static LocalDate dateOf(long batchId) {
    long ymd = batchId / 100L;
    return LocalDate.of(
        (int) (ymd / 10_000L), (int) (ymd / 100L % 100L), (int) (ymd % 100L));
  }

  public static int seqOf(long batchId) {
    return (int) (batchId % 100L);
  }

  /** Dạng người đọc được, để ghi log và đưa vào báo cáo. */
  public static String describe(long batchId) {
    return dateOf(batchId) + " lô " + seqOf(batchId);
  }
}
