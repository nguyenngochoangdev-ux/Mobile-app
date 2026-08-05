package vn.ptit.drl.credential;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Cấp {@code status_list_index} <b>ngẫu nhiên từ pool còn trống</b>, không tuần tự.
 *
 * <h2>Vì sao không cấp tuần tự — đây là một biện pháp riêng tư, không phải sở thích</h2>
 *
 * <p>Thu hồi credential phát sự kiện {@code StatusChanged(index)} <b>lên chuỗi công khai,
 * vĩnh viễn</b>. Nếu chỉ số được cấp tuần tự thì bản thân con số đã là một dấu thời gian:
 * index nhỏ = cấp sớm. Ai đối chiếu thứ tự đó với danh sách sinh viên theo khóa, theo lớp,
 * hay theo thứ tự nhập học là truy ngược được credential nào của ai — mà không cần chạm vào
 * máy chủ của trường. Cấp ngẫu nhiên cắt đứt tương quan đó. Xem {@code PROJECT.md} §2.3.
 *
 * <p><b>Đừng "đơn giản hóa" lớp này thành một bộ đếm.</b> Nó sẽ chạy đúng, mọi test nghiệp vụ
 * vẫn xanh, và biện pháp riêng tư biến mất không dấu vết. {@code StatusListIndexAllocatorTest}
 * có test chốt riêng chuyện đó.
 *
 * <h2>Thuật toán: bốc ngẫu nhiên rồi thử lại</h2>
 *
 * <p>Bốc một chỉ số ngẫu nhiên trong {@code [0, poolSize)}, hỏi xem đã dùng chưa, trùng thì
 * bốc lại. Với độ đầy {@code p}, số lần bốc kỳ vọng là {@code 1/(1−p)} — ở mức đầy dự kiến
 * (vài phần trăm) thì gần như luôn trúng ngay lần đầu.
 *
 * <p>Đã cân nhắc và <b>bỏ</b> phương án hoán vị giả ngẫu nhiên có khóa (Feistel): nó cho ánh
 * xạ song ánh nên không bao giờ trùng, nhưng đổi lại phải quản lý <b>thêm một khóa bí mật
 * nữa</b>, và lộ khóa đó là khôi phục được toàn bộ thứ tự cấp phát — đúng thứ lớp này sinh ra
 * để giấu. {@code PROJECT.md} §2.6 đã ghi việc giữ khóa là điểm yếu; thêm khóa thứ hai để
 * tiết kiệm vài lần truy vấn là đánh đổi sai.
 *
 * <h2>Ràng buộc UNIQUE mới là trọng tài cuối cùng</h2>
 *
 * <p>Lớp này trả về chỉ số còn trống <b>tại thời điểm hỏi</b>. Hai luồng cấp đồng thời vẫn có
 * thể bốc trúng cùng một số. Chốt chặn thật nằm ở
 * {@code UNIQUE KEY uk_cred_status_index} trong CSDL — bên gọi phải sẵn sàng nhận
 * {@code DataIntegrityViolationException} và cấp lại. Hệ chạy một instance nên xác suất này
 * rất nhỏ, nhưng "rất nhỏ" không phải "không có".
 */
public final class StatusListIndexAllocator {

  /** Nguồn sự thật về chỉ số đã dùng. Tuần 4 nối vào bảng {@code credentials}. */
  @FunctionalInterface
  public interface TakenIndexes {
    boolean isTaken(long index);
  }

  /**
   * Số lần bốc tối đa trước khi bỏ cuộc.
   *
   * <p>Ở độ đầy 50% thì kỳ vọng 2 lần bốc, xác suất cần quá 64 lần là {@code 2^-64}. Nên chạm
   * trần này nghĩa là pool đã gần đầy thật, không phải xui.
   */
  private static final int MAX_ATTEMPTS = 64;

  /** Vượt ngưỡng này thì cảnh báo — pool sắp hết, nên tăng trước khi nó chặn cấp phát. */
  public static final double OCCUPANCY_WARN_THRESHOLD = 0.5;

  private final long poolSize;
  private final RandomGenerator random;

  /**
   * @param poolSize Kích thước không gian chỉ số. Xem {@code docs/canonicalization.md} §10 về
   *     cách chọn — nó là một cái núm có hệ quả đo được lên chi phí gas thu hồi.
   */
  public StatusListIndexAllocator(long poolSize) {
    // SecureRandom, KHÔNG phải java.util.Random: Random là bộ sinh tuyến tính đồng dư, chỉ
    // cần vài giá trị đầu ra là khôi phục được trạng thái và đoán được mọi giá trị sau. Dùng
    // nhầm ở đây làm chỉ số "ngẫu nhiên" trở nên dự đoán được, tức là mất sạch tác dụng.
    this(poolSize, new SecureRandom());
  }

  /** Dùng cho test — cho phép tiêm bộ sinh tất định. Đừng dùng ở mã chạy thật. */
  StatusListIndexAllocator(long poolSize, RandomGenerator random) {
    if (poolSize <= 0) {
      throw new IllegalArgumentException("poolSize phải dương, nhận được: " + poolSize);
    }
    this.poolSize = poolSize;
    this.random = random;
  }

  public long poolSize() {
    return poolSize;
  }

  /**
   * Cấp một chỉ số còn trống.
   *
   * @throws IllegalStateException nếu bốc {@value #MAX_ATTEMPTS} lần vẫn trúng chỗ đã dùng —
   *     pool gần đầy, phải tăng {@code drl.credential.status-list-pool-size}.
   */
  public long allocate(TakenIndexes taken) {
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      long candidate = random.nextLong(poolSize);
      if (!taken.isTaken(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException(
        "Bốc " + MAX_ATTEMPTS + " lần đều trúng chỉ số đã dùng — pool " + poolSize
            + " gần đầy. Tăng `drl.credential.status-list-pool-size`."
            + " KHÔNG được chuyển sang cấp tuần tự để chữa: xem PROJECT.md §2.3.");
  }

  /**
   * Cấp {@code count} chỉ số phân biệt trong một lượt — dùng khi cấp credential hàng loạt
   * cuối kỳ (tuần 5 chấm 500 sinh viên).
   *
   * <p>Tự loại trùng <b>trong nội bộ lô</b>, việc mà gọi {@link #allocate} nhiều lần không làm
   * được nếu các bản ghi chưa kịp ghi xuống CSDL.
   */
  public List<Long> allocateBatch(int count, TakenIndexes taken) {
    if (count < 0) {
      throw new IllegalArgumentException("count không được âm: " + count);
    }
    if (count > poolSize) {
      throw new IllegalArgumentException(
          "Xin " + count + " chỉ số nhưng pool chỉ có " + poolSize);
    }

    Set<Long> issued = new HashSet<>(Math.max(16, count * 2));
    List<Long> out = new ArrayList<>(count);

    // Chỉ số vừa cấp trong lô này cũng tính là ĐÃ DÙNG, dù chưa nằm trong CSDL.
    TakenIndexes withBatch = i -> issued.contains(i) || taken.isTaken(i);

    for (int i = 0; i < count; i++) {
      long index = allocate(withBatch);
      issued.add(index);
      out.add(index);
    }
    return out;
  }

  /** Độ đầy hiện tại, dùng để cảnh báo trước khi pool chặn cấp phát. */
  public double occupancy(long usedCount) {
    return (double) usedCount / (double) poolSize;
  }
}
