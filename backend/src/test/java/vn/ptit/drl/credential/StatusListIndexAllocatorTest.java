package vn.ptit.drl.credential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test cho {@link StatusListIndexAllocator}.
 *
 * <p>Nhóm quan trọng nhất là {@link KhongDuocTuanTu}: nếu ai đó "đơn giản hóa" lớp này thành
 * một bộ đếm thì mọi test nghiệp vụ khác vẫn xanh, chỉ nhóm đó đỏ. Biện pháp riêng tư ở
 * {@code PROJECT.md} §2.3 sống hay chết ở đấy.
 */
class StatusListIndexAllocatorTest {

  /** Tập chỉ số đã dùng, đóng vai bảng {@code credentials} trong test. */
  private static final class FakeTaken implements StatusListIndexAllocator.TakenIndexes {
    private final Set<Long> used = new HashSet<>();

    @Override
    public boolean isTaken(long index) {
      return used.contains(index);
    }

    void markUsed(long index) {
      used.add(index);
    }

    int size() {
      return used.size();
    }
  }

  // ------------------------------------------------------------- tính đúng đắn

  @Test
  @DisplayName("Chỉ số cấp ra luôn nằm trong [0, poolSize)")
  void trongPhamVi() {
    var allocator = new StatusListIndexAllocator(1024);
    var taken = new FakeTaken();

    for (int i = 0; i < 500; i++) {
      long index = allocator.allocate(taken);
      assertTrue(index >= 0 && index < 1024, "ngoài phạm vi: " + index);
      taken.markUsed(index);
    }
  }

  @Test
  @DisplayName("Không bao giờ cấp lại chỉ số đã dùng")
  void khongCapTrung() {
    var allocator = new StatusListIndexAllocator(4096);
    var taken = new FakeTaken();
    Set<Long> seen = new HashSet<>();

    for (int i = 0; i < 2000; i++) {
      long index = allocator.allocate(taken);
      assertTrue(seen.add(index), "cấp trùng chỉ số " + index);
      taken.markUsed(index);
    }
    assertEquals(2000, taken.size());
  }

  @Test
  @DisplayName("Bốc ngẫu nhiên KHÔNG lấp đầy được 100% pool — giới hạn có chủ ý")
  void khongLapDayDuoc100PhanTram() {
    // TÍNH CHẤT THẬT, KHÔNG PHẢI LỖI. Ở ô trống cuối cùng của pool N, mỗi lần bốc chỉ có
    // xác suất 1/N trúng, nên trượt cả 64 lần là chuyện thường xuyên xảy ra.
    //
    // Đây là đánh đổi có chủ ý: pool được cỡ sao cho độ đầy ở mức vài phần trăm, và khi nó
    // gần đầy ta MUỐN vỡ ồn ào để người vận hành tăng pool. Phương án "chữa" bằng cách quét
    // tuyến tính tìm ô trống sẽ tốn một truy vấn CSDL cho mỗi chỉ số (pool mặc định là 2^20)
    // và tệ hơn nữa là GIẤU MẤT vấn đề hết chỗ.
    //
    // Test này tồn tại để chặn một "sửa lỗi" như thế được thêm vào mà không ai cân nhắc.
    var allocator = new StatusListIndexAllocator(64);
    var taken = new FakeTaken();
    Set<Long> seen = new HashSet<>();

    int capDuoc = 0;
    try {
      for (int i = 0; i < 64; i++) {
        long index = allocator.allocate(taken);
        assertTrue(index >= 0 && index < 64, "ngoài phạm vi: " + index);
        assertTrue(seen.add(index), "cấp trùng chỉ số " + index);
        taken.markUsed(index);
        capDuoc++;
      }
    } catch (IllegalStateException expected) {
      // Bỏ cuộc đúng cách: ném lỗi, không cấp bừa và không cấp trùng.
    }

    // Dù bỏ cuộc ở đâu, hai bất biến phải giữ: không trùng, không ngoài phạm vi (đã kiểm
    // ở trên). Và phải đi được kha khá trước khi bỏ cuộc.
    assertTrue(capDuoc >= 32, "bỏ cuộc quá sớm, mới cấp được " + capDuoc + "/64");
    assertEquals(capDuoc, seen.size());
  }

  @Test
  @DisplayName("Pool đầy thì NÉM LỖI, không cấp bừa")
  void poolDayThiNemLoi() {
    var allocator = new StatusListIndexAllocator(8);
    var taken = new FakeTaken();
    for (long i = 0; i < 8; i++) {
      taken.markUsed(i);
    }

    var e = assertThrows(IllegalStateException.class, () -> allocator.allocate(taken));
    assertTrue(e.getMessage().contains("pool"), "thông báo lỗi phải chỉ ra pool đầy");
    // Thông báo phải nói rõ đừng chữa bằng cách cấp tuần tự — đó là cái bẫy hiển nhiên nhất
    // khi ai đó gặp lỗi này lúc đang vội.
    assertTrue(e.getMessage().contains("2.3") || e.getMessage().contains("tuần tự"));
  }

  @Test
  @DisplayName("poolSize không hợp lệ bị từ chối ngay lúc dựng")
  void poolSizeKhongHopLe() {
    assertThrows(IllegalArgumentException.class, () -> new StatusListIndexAllocator(0));
    assertThrows(IllegalArgumentException.class, () -> new StatusListIndexAllocator(-1));
  }

  // ----------------------------------------------------------------- cấp lô

  @Test
  @DisplayName("Cấp lô trả về các chỉ số phân biệt, kể cả khi chưa ghi xuống CSDL")
  void capLoPhanBiet() {
    // Đây là lý do allocateBatch tồn tại: gọi allocate() 500 lần liên tiếp trước khi commit
    // sẽ cấp trùng, vì `taken` chưa thấy các bản ghi đang chờ ghi.
    var allocator = new StatusListIndexAllocator(1 << 16);
    var taken = new FakeTaken();

    List<Long> batch = allocator.allocateBatch(500, taken);

    assertEquals(500, batch.size());
    assertEquals(500, new HashSet<>(batch).size(), "lô có chỉ số trùng nhau");
  }

  @Test
  @DisplayName("Cấp lô tránh cả chỉ số đã có trong CSDL")
  void capLoTranhChiSoDaCo() {
    var allocator = new StatusListIndexAllocator(256);
    var taken = new FakeTaken();
    for (long i = 0; i < 100; i++) {
      taken.markUsed(i);
    }

    List<Long> batch = allocator.allocateBatch(100, taken);

    for (long index : batch) {
      assertFalse(index < 100, "cấp trúng chỉ số đã dùng: " + index);
    }
  }

  @Test
  @DisplayName("Xin nhiều hơn sức chứa của pool bị từ chối")
  void xinQuaSucChua() {
    var allocator = new StatusListIndexAllocator(10);
    assertThrows(
        IllegalArgumentException.class, () -> allocator.allocateBatch(11, new FakeTaken()));
    assertThrows(
        IllegalArgumentException.class, () -> allocator.allocateBatch(-1, new FakeTaken()));
  }

  @Test
  @DisplayName("Cấp lô 0 phần tử trả về danh sách rỗng")
  void capLoRong() {
    var allocator = new StatusListIndexAllocator(16);
    assertTrue(allocator.allocateBatch(0, new FakeTaken()).isEmpty());
  }

  // ------------------------------------------------- NHÓM QUAN TRỌNG NHẤT

  @Nested
  @DisplayName("Không được cấp tuần tự — biện pháp riêng tư PROJECT.md §2.3")
  class KhongDuocTuanTu {

    @Test
    @DisplayName("Dãy cấp ra KHÔNG tăng dần")
    void khongTangDan() {
      // Nếu ai đó thay thuật toán bằng một bộ đếm, test này đỏ. Đó là toàn bộ mục đích
      // của nó — mọi test nghiệp vụ khác vẫn sẽ xanh với một bộ đếm.
      var allocator = new StatusListIndexAllocator(1 << 20);
      var taken = new FakeTaken();

      List<Long> seq = new ArrayList<>();
      for (int i = 0; i < 200; i++) {
        long index = allocator.allocate(taken);
        seq.add(index);
        taken.markUsed(index);
      }

      boolean tangDan = true;
      for (int i = 1; i < seq.size(); i++) {
        if (seq.get(i) <= seq.get(i - 1)) {
          tangDan = false;
          break;
        }
      }
      assertFalse(tangDan, "dãy chỉ số tăng dần — đã bị thay bằng bộ đếm?");
    }

    @Test
    @DisplayName("Chỉ số đầu tiên không bám quanh 0")
    void khongBatDauTuKhong() {
      // Bộ đếm bắt đầu từ 0. 30 lần cấp đầu tiên đều rơi vào [0, 1000) trên pool 2^20 là
      // chuyện gần như không thể xảy ra với bốc ngẫu nhiên thật.
      var allocator = new StatusListIndexAllocator(1 << 20);
      var taken = new FakeTaken();

      int ganKhong = 0;
      for (int i = 0; i < 30; i++) {
        long index = allocator.allocate(taken);
        taken.markUsed(index);
        if (index < 1000) {
          ganKhong++;
        }
      }
      assertTrue(ganKhong < 15, "quá nhiều chỉ số bám quanh 0: " + ganKhong + "/30");
    }

    @Test
    @DisplayName("Chỉ số trải đều khắp pool, không dồn vào một góc")
    void traiDeuKhapPool() {
      // Chia pool thành 8 khoảng bằng nhau, cấp 800 chỉ số. Ngẫu nhiên đều thì mỗi khoảng
      // nhận ~100. Cho biên rộng để test không mong manh, nhưng vẫn đủ chặt để bắt được
      // mọi kiểu cấp dồn cục.
      var allocator = new StatusListIndexAllocator(1 << 20);
      var taken = new FakeTaken();
      int[] buckets = new int[8];
      long bucketWidth = (1 << 20) / 8;

      for (int i = 0; i < 800; i++) {
        long index = allocator.allocate(taken);
        taken.markUsed(index);
        buckets[(int) (index / bucketWidth)]++;
      }

      for (int b = 0; b < 8; b++) {
        assertTrue(buckets[b] > 40, "khoảng " + b + " chỉ có " + buckets[b] + " chỉ số");
        assertTrue(buckets[b] < 180, "khoảng " + b + " có tới " + buckets[b] + " chỉ số");
      }
    }

    @Test
    @DisplayName("Hai allocator độc lập cho ra hai dãy khác nhau")
    void haiAllocatorKhacNhau() {
      // Chốt rằng nguồn ngẫu nhiên không phải hằng số hay seed cố định.
      List<Long> a = new ArrayList<>();
      List<Long> b = new ArrayList<>();
      for (int i = 0; i < 20; i++) {
        a.add(new StatusListIndexAllocator(1 << 20).allocate(new FakeTaken()));
        b.add(new StatusListIndexAllocator(1 << 20).allocate(new FakeTaken()));
      }
      assertFalse(a.equals(b), "hai allocator cho ra dãy giống hệt nhau — seed cố định?");
    }
  }

  // ------------------------------------------------------- số lần bốc kỳ vọng

  @Test
  @DisplayName("Số lần bốc khớp lý thuyết 1/(1-p) — con số cho báo cáo")
  void soLanBocKhopLyThuyet() {
    // Đo trực tiếp thay vì tin công thức. Ở độ đầy 50% thì kỳ vọng 2 lần bốc.
    final int poolSize = 4096;
    final int prefill = poolSize / 2;

    var taken = new FakeTaken();
    var counter = new int[1];
    // Bọc để đếm số lần allocator hỏi "đã dùng chưa" — chính là số lần bốc.
    StatusListIndexAllocator.TakenIndexes counted =
        i -> {
          counter[0]++;
          return taken.isTaken(i);
        };

    var seeder = new StatusListIndexAllocator(poolSize);
    for (int i = 0; i < prefill; i++) {
      taken.markUsed(seeder.allocate(taken));
    }

    counter[0] = 0;
    var allocator = new StatusListIndexAllocator(poolSize);
    final int draws = 200;
    for (int i = 0; i < draws; i++) {
      long index = allocator.allocate(counted);
      taken.markUsed(index);
    }

    double trungBinh = (double) counter[0] / draws;
    // Lý thuyết ~2,0 ở độ đầy 50% (tăng dần trong lúc chạy nên nhích lên chút).
    assertTrue(trungBinh > 1.3 && trungBinh < 3.5, "số lần bốc trung bình: " + trungBinh);
  }

  @Test
  @DisplayName("occupancy tính đúng")
  void doDay() {
    var allocator = new StatusListIndexAllocator(1000);
    assertEquals(0.0, allocator.occupancy(0));
    assertEquals(0.5, allocator.occupancy(500));
    assertEquals(1.0, allocator.occupancy(1000));
  }

  @Test
  @DisplayName("Tiêm được bộ sinh tất định để test tái lập")
  void tiemBoSinhTatDinh() {
    RandomGenerator fixed = new java.util.Random(42);
    var allocator = new StatusListIndexAllocator(1000, fixed);
    long first = allocator.allocate(new FakeTaken());

    RandomGenerator fixed2 = new java.util.Random(42);
    var allocator2 = new StatusListIndexAllocator(1000, fixed2);
    assertEquals(first, allocator2.allocate(new FakeTaken()));
  }
}
