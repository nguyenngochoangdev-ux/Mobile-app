package vn.ptit.drl.anchor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import vn.ptit.drl.attendance.Attendance;
import vn.ptit.drl.attendance.AttendanceMethod;
import vn.ptit.drl.attendance.AttendanceRepository;
import vn.ptit.drl.event.Event;
import vn.ptit.drl.event.EventRepository;
import vn.ptit.drl.event.EventStatus;
import vn.ptit.drl.identity.Student;
import vn.ptit.drl.identity.StudentRepository;
import vn.ptit.drl.org.Organization;
import vn.ptit.drl.org.OrganizationRepository;

/**
 * Chạy TOÀN BỘ job neo trên chuỗi Hardhat cục bộ, với CSDL thật.
 *
 * <p>Đây là bài test khép kín cuối cùng của tầng neo: bản ghi trong MySQL → payload → leaf →
 * cây Merkle → giao dịch lên chuỗi → {@code anchor_batches} + {@code anchor_leaves} →
 * {@code leaf_hash} ghi về bảng gốc → proof đọc từ CSDL verify được về root đọc từ chuỗi.
 *
 * <p>Không chạy trên Amoy vì mỗi {@code (domain, batchId)} chỉ neo được một lần.
 *
 * <p><b>Mỗi lần chạy phải khởi động lại {@code hardhat node}.</b> {@code batchId} suy từ ngày
 * (UTC) nên lần chạy sau sinh lại đúng những số đã dùng, còn chuỗi thì nhớ vĩnh viễn — gửi
 * lại là bị revert {@code RootAlreadyAnchored}. Đó là hành vi đúng, không phải lỗi: nó chính
 * là thứ chặn việc neo hai root khác nhau cho cùng một lô.
 *
 * <pre>
 *   cd contracts &amp;&amp; npx hardhat node          # khởi động lại mỗi lần chạy test
 *   cd contracts &amp;&amp; npm run deploy:local
 *   $env:LOCAL_CHAIN_TEST="true"; $env:LOCAL_ANCHOR_REGISTRY="0x..."
 *   .\scripts\test-backend.ps1 AnchorJobLocalChainTest
 * </pre>
 *
 * <p><b>Test này neo MỌI bản ghi đang chờ</b>, kể cả dữ liệu dev không do nó tạo ra —
 * {@code runAll()} không có cách nào lọc riêng. {@link #cleanUp()} trả những bản ghi đó về
 * nguyên trạng, nếu không chúng sẽ mang {@code leaf_hash} trỏ tới root của chuỗi cục bộ và
 * không bao giờ được neo thật nữa.
 */
@EnabledIfEnvironmentVariable(named = "LOCAL_CHAIN_TEST", matches = "true")
@SpringBootTest
class AnchorJobLocalChainTest {

  private static final String HARDHAT_KEY_0 =
      "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

  @DynamicPropertySource
  static void chainProperties(DynamicPropertyRegistry registry) {
    registry.add("drl.anchor.enabled", () -> "true");
    registry.add("drl.anchor.rpc-url",
        () -> System.getenv().getOrDefault("LOCAL_RPC_URL", "http://127.0.0.1:8545"));
    registry.add("drl.anchor.chain-id", () -> "31337");
    registry.add("drl.anchor.private-key", () -> HARDHAT_KEY_0);
    registry.add("drl.anchor.anchor-registry-address",
        () -> System.getenv("LOCAL_ANCHOR_REGISTRY"));
    // Tắt lịch: test tự gọi runAll(), không muốn cron chen ngang.
    registry.add("drl.anchor.cron", () -> "-");
  }

  @Autowired AnchorJob job;
  @Autowired AnchorRegistryClient client;
  @Autowired JdbcTemplate jdbc;
  @Autowired AttendanceRepository attendanceRepository;
  @Autowired EventRepository eventRepository;
  @Autowired StudentRepository studentRepository;
  @Autowired OrganizationRepository organizationRepository;

  private final List<Long> createdAttendanceIds = new ArrayList<>();
  private Long createdEventId;

  /**
   * Bản ghi vốn đã chờ neo TRƯỚC khi test chạy.
   *
   * <p>{@code runAll()} neo <b>mọi</b> thứ đang chờ, không chỉ dữ liệu test tạo ra — nên nó
   * kéo theo cả bản ghi dev thật và gắn cho chúng một {@code leaf_hash} trỏ tới root của
   * <b>chuỗi cục bộ</b>, thứ không tồn tại trên Amoy. Đã dính một lần: bản ghi dev bị đánh
   * dấu đã neo và sẽ không bao giờ được neo thật nữa.
   *
   * <p>Ghi lại từ đầu để {@link #cleanUp()} trả chúng về nguyên trạng.
   */
  private List<Long> preExistingPendingIds = new ArrayList<>();

  /** Dữ liệu riêng của test, dọn sạch sau mỗi lần chạy — không đụng dữ liệu dev. */
  @BeforeEach
  void seed() {
    preExistingPendingIds = jdbc.queryForList(
        "SELECT a.id FROM attendances a JOIN events e ON e.id = a.event_id"
            + " WHERE a.leaf_hash IS NULL AND e.end_at < NOW()",
        Long.class);

    Organization org = organizationRepository.findAll().stream().findFirst()
        .orElseThrow(() -> new IllegalStateException("CSDL chưa có tổ chức nào"));
    List<Student> students = studentRepository.findAll().stream().limit(9).toList();
    if (students.size() < 9) {
      throw new IllegalStateException("CSDL cần ít nhất 9 sinh viên để chạy test này");
    }

    Event e = new Event();
    e.setOrg(org);
    e.setTitle("Su kien test neo");
    e.setType("HOI_THAO");
    e.setStartAt(Instant.now().minus(3, ChronoUnit.HOURS));
    // ĐÃ KẾT THÚC — điều kiện bắt buộc để được neo, xem AttendanceAnchorSource.
    e.setEndAt(Instant.now().minus(1, ChronoUnit.HOURS));
    e.setStatus(EventStatus.CLOSED);
    e.setSecretKey("test-secret-neo".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    createdEventId = eventRepository.save(e).getId();

    // 9 lá: số LẺ nên cây có nút bị đẩy lên — trường hợp dễ sai nhất.
    // Phủ đủ bốn method và cả bản ghi thiếu toạ độ / chưa check-out.
    AttendanceMethod[] methods = {
        AttendanceMethod.QR_SCAN, AttendanceMethod.QR_SHOW, AttendanceMethod.MANUAL,
        AttendanceMethod.OFFLINE_SYNC, AttendanceMethod.QR_SCAN, AttendanceMethod.MANUAL,
        AttendanceMethod.QR_SCAN, AttendanceMethod.QR_SHOW, AttendanceMethod.OFFLINE_SYNC};

    for (int i = 0; i < 9; i++) {
      Attendance a = new Attendance();
      a.setEvent(e);
      a.setStudent(students.get(i));
      a.setMethod(methods[i]);
      a.setCheckinAt(Instant.now().minus(2, ChronoUnit.HOURS));
      a.setCheckoutAt(i % 3 == 0 ? Instant.now().minus(90, ChronoUnit.MINUTES) : null);
      a.setDeviceFp(i % 2 == 0 ? "device-" + i : null);
      a.setLat(i % 4 == 0 ? new BigDecimal("21.0285000") : null);
      a.setLng(i % 4 == 0 ? new BigDecimal("105.8542000") : null);
      a.setVerified(methods[i] != AttendanceMethod.MANUAL);
      a.setGeofenceOk(i % 4 == 0 ? Boolean.TRUE : null);
      a.setNonce(HexFormat.of().parseHex(LeafHasher.newNonce().substring(2)));
      createdAttendanceIds.add(attendanceRepository.save(a).getId());
    }
  }

  @AfterEach
  void cleanUp() {
    if (!createdAttendanceIds.isEmpty()) {
      // `IN ()` rỗng là lỗi cú pháp MySQL — phải chặn, vì nếu seed hỏng giữa chừng thì
      // danh sách rỗng và lỗi dọn dẹp sẽ che mất lỗi thật.
      String placeholders =
          String.join(",", java.util.Collections.nCopies(createdAttendanceIds.size(), "?"));
      jdbc.update("DELETE FROM anchor_leaves WHERE source_table = 'attendances'"
          + " AND source_id IN (" + placeholders + ")", createdAttendanceIds.toArray());
      attendanceRepository.deleteAllById(createdAttendanceIds);
    }
    if (createdEventId != null) {
      eventRepository.deleteById(createdEventId);
    }

    // Trả các bản ghi dev bị neo lây về nguyên trạng — chúng đang mang leaf_hash trỏ tới
    // root của chuỗi cục bộ, thứ không tồn tại trên Amoy.
    if (!preExistingPendingIds.isEmpty()) {
      String ph = String.join(",", java.util.Collections.nCopies(preExistingPendingIds.size(), "?"));
      Object[] ids = preExistingPendingIds.toArray();
      jdbc.update("DELETE FROM anchor_leaves WHERE source_table = 'attendances'"
          + " AND source_id IN (" + ph + ")", ids);
      jdbc.update("UPDATE attendances SET leaf_hash = NULL WHERE id IN (" + ph + ")", ids);
    }

    createdAttendanceIds.clear();
    preExistingPendingIds = new ArrayList<>();
    createdEventId = null;
  }

  /**
   * Xoá lô ở {@code @AfterAll}, KHÔNG phải {@code @AfterEach}.
   *
   * <p>{@code nextBatchId} đếm số lô đã có trong ngày để lấy số kế tiếp. Xoá lô sau mỗi test
   * thì số thứ tự quay về 1, trong khi chuỗi vẫn nhớ lô cũ — test thứ hai sẽ gửi lại
   * {@code 2026xxxx01} và bị revert {@code RootAlreadyAnchored}. Giữ lô đến hết lớp thì số
   * thứ tự tăng đúng như lúc chạy thật.
   */
  @org.junit.jupiter.api.AfterAll
  static void cleanUpBatches(@Autowired JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM anchor_leaves");
    jdbc.update("DELETE FROM anchor_batches");
  }

  @Test
  @DisplayName("Job neo khép kín: CSDL → chuỗi → proof verify được về root trên chuỗi")
  void jobNeoKhepKin() throws Exception {
    List<AnchorJob.Result> results = job.runAll();

    AnchorJob.Result attend = results.stream()
        .filter(r -> r.domain() == AnchorDomain.ATTEND)
        .findFirst()
        .orElseThrow(() -> new AssertionError("job không neo miền ATTEND"));

    assertTrue(attend.leafCount() >= 9, "lô phải chứa ít nhất 9 bản ghi vừa tạo");
    assertNotNull(attend.txHash());
    System.out.printf("  lo %s · %d la · tx %s · gas %d%n",
        AnchorBatchId.describe(attend.batchId()), attend.leafCount(),
        attend.txHash(), attend.gasUsed());

    // 1. Root trên chuỗi khớp root job tính.
    byte[] onChain = client.getRoot(AnchorDomain.ATTEND, attend.batchId());
    assertArrayEquals(attend.root(), onChain, "root trên chuỗi lệch root job tính");

    // 2. Lô được ghi đầy đủ vào CSDL.
    Map<String, Object> batch = jdbc.queryForMap(
        "SELECT batch_id, leaf_count, tx_hash, block_number, error_message"
            + " FROM anchor_batches WHERE domain = ? AND batch_id = ?",
        AnchorDomain.ATTEND.name(), attend.batchId());

    assertEquals(attend.txHash(), batch.get("tx_hash"));
    assertNotNull(batch.get("block_number"));
    assertEquals(null, batch.get("error_message"));

    // 3. leaf_hash đã ghi về bảng gốc — bản ghi không bị neo lại lần sau.
    for (Long id : createdAttendanceIds) {
      Attendance a = attendanceRepository.findById(id).orElseThrow();
      assertNotNull(a.getLeafHash(), "bản ghi " + id + " chưa được đánh dấu đã neo");
      assertEquals(32, a.getLeafHash().length);
    }

    // 4. Điều quan trọng nhất: proof LẤY TỪ CSDL verify được về root ĐỌC TỪ CHUỖI.
    //    Đây đúng là phép tính mà verifier tĩnh sẽ chạy trong trình duyệt.
    List<Map<String, Object>> leaves = jdbc.queryForList(
        "SELECT l.leaf_hash, l.proof_json FROM anchor_leaves l"
            + " JOIN anchor_batches b ON b.id = l.batch_id"
            + " WHERE b.domain = ? AND b.batch_id = ?",
        AnchorDomain.ATTEND.name(), attend.batchId());

    assertEquals(attend.leafCount(), leaves.size());

    for (Map<String, Object> row : leaves) {
      byte[] leaf = (byte[]) row.get("leaf_hash");
      List<byte[]> siblings = parseProof((String) row.get("proof_json"));
      assertTrue(
          MerkleService.verify(leaf, siblings, onChain),
          "proof trong CSDL không verify được về root trên chuỗi");
    }
  }

  @Test
  @DisplayName("Chạy lần hai không neo lại bản ghi đã neo")
  void chayLaiKhongNeoTrung() throws Exception {
    job.runAll();

    // Lần hai: mọi bản ghi đã có leaf_hash nên pending() trả rỗng.
    List<AnchorJob.Result> lanHai = job.runAll();

    assertFalse(
        lanHai.stream().anyMatch(r -> r.domain() == AnchorDomain.ATTEND),
        "job neo lại dữ liệu đã neo — pending() không lọc theo leaf_hash?");
  }

  private static List<byte[]> parseProof(String json) {
    List<byte[]> out = new ArrayList<>();
    String body = json.trim();
    body = body.substring(1, body.length() - 1).trim(); // bỏ [ ]
    if (body.isEmpty()) {
      return out;
    }
    for (String part : body.split(",")) {
      String hex = part.trim().replace("\"", "").replace("0x", "");
      out.add(HexFormat.of().parseHex(hex));
    }
    return out;
  }
}
