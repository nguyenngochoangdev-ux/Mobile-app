package vn.ptit.drl.anchor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.ptit.drl.common.config.DrlProperties;

/**
 * Job neo hằng đêm: gom bản ghi đã chốt của từng miền, dựng cây Merkle, đẩy root lên chuỗi.
 *
 * <h2>Thứ tự ghi và gửi — sai là mất bản ghi hoặc neo trùng</h2>
 *
 * <p>Có hai thao tác không thể gộp thành một giao dịch: ghi CSDL và gửi giao dịch lên chuỗi.
 * Cái nào trước cũng có một cách hỏng, nên phải chọn cách hỏng <b>sửa được</b>:
 *
 * <ol>
 *   <li><b>Ghi lô xuống CSDL trước</b> ({@code tx_hash} NULL), kèm toàn bộ lá và proof.
 *   <li><b>Rồi mới gửi giao dịch.</b>
 *   <li>Xong thì cập nhật {@code tx_hash}, {@code block_number}, và ghi {@code leaf_hash} về
 *       bảng gốc.
 * </ol>
 *
 * <p>Nếu bước 2 hỏng, lô nằm lại với {@code tx_hash} NULL và {@code leaf_hash} ở bảng gốc vẫn
 * NULL. Lần chạy sau {@link #resumePending} nhặt đúng lô đó lên và neo lại <b>cùng root, cùng
 * batchId</b> — không gom lô mới, không neo trùng dữ liệu.
 *
 * <p>Làm ngược lại (gửi trước, ghi sau) thì giao dịch đã lên chuỗi mà CSDL không biết: lần
 * sau job gom lại đúng những bản ghi đó vào một batchId khác và neo thêm một lần nữa, để lại
 * hai root cho cùng một tập dữ liệu.
 *
 * <p>Còn ghi {@code leaf_hash} <b>trước</b> khi giao dịch thành công là hỏng nặng nhất:
 * {@link AnchorSource#pending} lọc theo {@code leaf_hash IS NULL}, nên các bản ghi đó sẽ
 * <b>không bao giờ được neo lại</b> — mất im lặng.
 *
 * <h2>Ranh giới module</h2>
 *
 * <p>Lớp này ở module {@code anchor} và <b>không import gì từ nghiệp vụ</b> (PROJECT.md §5).
 * Nó chỉ biết {@link AnchorSource}; các module nghiệp vụ hiện thực giao diện đó.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnchorJob {

  /**
   * Số lá tối đa một lô.
   *
   * <p>Không phải giới hạn gas — chi phí neo không phụ thuộc N (phép đo #1). Đây là giới hạn
   * bộ nhớ và thời gian dựng cây, cộng với việc lô càng lớn thì một lần hỏng càng đắt.
   */
  private static final int MAX_LEAVES_PER_BATCH = 5000;

  private final DrlProperties props;
  private final JdbcTemplate jdbc;
  private final List<AnchorSource> sources;
  private final ObjectProvider<AnchorRegistryClient> clientProvider;

  /** Kết quả một lượt neo, để log và để test đối chiếu. */
  public record Result(AnchorDomain domain, long batchId, byte[] root, int leafCount,
                       String txHash, long gasUsed) {}

  // ------------------------------------------------------------------ lịch

  @Scheduled(cron = "${drl.anchor.cron}")
  public void scheduled() {
    if (!props.anchor().enabled()) {
      return;
    }
    try {
      runAll();
    } catch (Exception e) {
      // Job hằng đêm không được làm sập ứng dụng. Lỗi đã ghi vào anchor_batches.
      log.error("Job neo that bai", e);
    }
  }

  // ------------------------------------------------------------------ chạy

  /** Neo mọi miền có bản ghi chờ. Trả về các lô đã lên chuỗi thành công. */
  public List<Result> runAll() {
    AnchorRegistryClient client = requireClient();
    List<Result> results = new ArrayList<>();

    for (AnchorSource source : sources) {
      results.addAll(resumePending(source, client));
      runOnce(source, client).ifPresent(results::add);
    }
    return results;
  }

  /** Neo một miền. {@code Optional.empty()} nghĩa là không có gì chờ neo. */
  public java.util.Optional<Result> runOnce(AnchorSource source, AnchorRegistryClient client) {
    AnchorDomain domain = source.domain();
    List<AnchorSource.Item> items = source.pending(MAX_LEAVES_PER_BATCH);

    if (items.isEmpty()) {
      log.debug("{}: khong co ban ghi cho neo", domain);
      return java.util.Optional.empty();
    }

    // Dựng lá theo đúng thứ tự pending() trả về — thứ tự này là phần của bằng chứng, vì
    // MerkleService giữ nguyên thứ tự lá (docs/canonicalization.md §8 quy ước 3).
    List<byte[]> leaves = new ArrayList<>(items.size());
    Map<Long, byte[]> leafBySource = new LinkedHashMap<>();
    for (AnchorSource.Item item : items) {
      byte[] leaf = LeafHasher.leaf(domain, item.payload());
      leaves.add(leaf);
      leafBySource.put(item.sourceId(), leaf);
    }

    byte[] root = MerkleService.root(leaves);
    long batchId = nextBatchId(domain);

    // Bước 1: ghi lô + lá + proof, chưa có tx_hash.
    long rowId = insertBatch(domain, batchId, root, items.size());
    insertLeaves(rowId, source, items, leaves);

    log.info("{}: {} la, batchId {} ({}), root {}",
        domain, items.size(), batchId, AnchorBatchId.describe(batchId), hex(root));

    // Bước 2 + 3.
    return java.util.Optional.of(
        submit(client, source, domain, batchId, root, items.size(), rowId, leafBySource));
  }

  /**
   * Neo lại các lô đã dựng cây nhưng chưa lên chuỗi.
   *
   * <p>Dùng lại <b>chính batchId và root cũ</b>. Gom lô mới ở đây là sai: những bản ghi đó đã
   * có proof tính theo root cũ nằm trong {@code anchor_leaves}, nên đổi root là mọi proof đã
   * lưu thành vô hiệu.
   */
  private List<Result> resumePending(AnchorSource source, AnchorRegistryClient client) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, batch_id, merkle_root, leaf_count FROM anchor_batches"
            + " WHERE domain = ? AND tx_hash IS NULL ORDER BY batch_id",
        source.domain().name());

    List<Result> results = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      long rowId = ((Number) row.get("id")).longValue();
      long batchId = ((Number) row.get("batch_id")).longValue();
      byte[] root = (byte[]) row.get("merkle_root");
      int leafCount = ((Number) row.get("leaf_count")).intValue();

      log.warn("{}: neo lai lo {} da dung cay nhung chua len chuoi",
          source.domain(), AnchorBatchId.describe(batchId));

      Map<Long, byte[]> leafBySource = jdbc.query(
          "SELECT source_id, leaf_hash FROM anchor_leaves WHERE batch_id = ?",
          rs -> {
            Map<Long, byte[]> m = new HashMap<>();
            while (rs.next()) {
              m.put(rs.getLong("source_id"), rs.getBytes("leaf_hash"));
            }
            return m;
          },
          rowId);

      results.add(submit(client, source, source.domain(), batchId, root, leafCount, rowId,
          leafBySource));
    }
    return results;
  }

  /** Gửi giao dịch, rồi cập nhật CSDL. Ném ra ngoài nếu chuỗi từ chối. */
  private Result submit(AnchorRegistryClient client, AnchorSource source, AnchorDomain domain,
                        long batchId, byte[] root, int leafCount, long rowId,
                        Map<Long, byte[]> leafBySource) {
    try {
      TransactionReceipt receipt = client.anchor(domain, batchId, root, leafCount);

      jdbc.update(
          "UPDATE anchor_batches SET tx_hash = ?, block_number = ?, anchored_at = ?,"
              + " error_message = NULL WHERE id = ?",
          receipt.getTransactionHash(),
          receipt.getBlockNumber().longValueExact(),
          java.sql.Timestamp.from(Instant.now()),
          rowId);

      // Chỉ đánh dấu bảng gốc SAU khi chuỗi đã nhận. Xem javadoc đầu lớp.
      source.saveLeafHashes(leafBySource);

      log.info("{}: neo xong lo {} · tx {} · gas {}",
          domain, batchId, receipt.getTransactionHash(), receipt.getGasUsed());

      return new Result(domain, batchId, root, leafCount,
          receipt.getTransactionHash(), receipt.getGasUsed().longValueExact());

    } catch (Exception e) {
      String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      jdbc.update(
          "UPDATE anchor_batches SET error_message = ? WHERE id = ?",
          message.length() > 500 ? message.substring(0, 500) : message,
          rowId);

      throw new IllegalStateException(
          "Neo " + domain + " lo " + batchId + " that bai. Lo van con trong anchor_batches"
              + " voi tx_hash NULL nen lan chay sau se neo lai chinh no.", e);
    }
  }

  // ------------------------------------------------------------------ CSDL

  /**
   * batchId khả dụng tiếp theo cho hôm nay.
   *
   * <p>Đếm số lô đã có của miền trong ngày rồi lấy số kế tiếp. Không dùng {@code MAX(batch_id)
   * + 1} vì như thế lô của ngày hôm trước sẽ đẩy số của hôm nay ra ngoài dải hợp lệ.
   */
  private long nextBatchId(AnchorDomain domain) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    long from = AnchorBatchId.of(today, 1);
    long to = AnchorBatchId.of(today, AnchorBatchId.MAX_SEQ);

    Integer used = jdbc.queryForObject(
        "SELECT COUNT(*) FROM anchor_batches WHERE domain = ? AND batch_id BETWEEN ? AND ?",
        Integer.class, domain.name(), from, to);

    int seq = (used == null ? 0 : used) + 1;
    if (seq > AnchorBatchId.MAX_SEQ) {
      throw new IllegalStateException(
          "Da neo " + AnchorBatchId.MAX_SEQ + " lo " + domain + " trong ngay hom nay."
              + " Kiem tra xem job co bi chay lap khong.");
    }
    return AnchorBatchId.of(today, seq);
  }

  @Transactional
  protected long insertBatch(AnchorDomain domain, long batchId, byte[] root, int leafCount) {
    jdbc.update(
        "INSERT INTO anchor_batches (domain, batch_id, merkle_root, leaf_count) VALUES (?,?,?,?)",
        domain.name(), batchId, root, leafCount);
    Long id = jdbc.queryForObject(
        "SELECT id FROM anchor_batches WHERE domain = ? AND batch_id = ?",
        Long.class, domain.name(), batchId);
    if (id == null) {
      throw new IllegalStateException("Khong doc lai duoc lo vua chen");
    }
    return id;
  }

  @Transactional
  protected void insertLeaves(long rowId, AnchorSource source, List<AnchorSource.Item> items,
                              List<byte[]> leaves) {
    List<Object[]> args = new ArrayList<>(items.size());
    for (int i = 0; i < items.size(); i++) {
      MerkleService.Proof proof = MerkleService.proof(leaves, i);

      // Proof lưu dạng mảng JSON hex — đúng thứ bundle của sinh viên mang đi, và đúng thứ
      // verifyProof() phía JS nhận. Tự dựng chuỗi thay vì gọi Jackson: nó chỉ là mảng chuỗi
      // hex, và tầng này cố ý không phụ thuộc Jackson (xem Jcs.java).
      StringBuilder json = new StringBuilder("[");
      for (int k = 0; k < proof.siblings().size(); k++) {
        if (k > 0) json.append(',');
        json.append('"').append(hex(proof.siblings().get(k))).append('"');
      }
      json.append(']');

      args.add(new Object[] {
          rowId, leaves.get(i), json.toString(), source.sourceTable(), items.get(i).sourceId()});
    }

    jdbc.batchUpdate(
        "INSERT INTO anchor_leaves (batch_id, leaf_hash, proof_json, source_table, source_id)"
            + " VALUES (?,?,?,?,?)",
        args);
  }

  private AnchorRegistryClient requireClient() {
    AnchorRegistryClient client = clientProvider.getIfAvailable();
    if (client == null) {
      throw new IllegalStateException(
          "Chuoi dang tat. Dat ANCHOR_ENABLED=true va dien AMOY_RPC_URL,"
              + " ANCHOR_PRIVATE_KEY, ANCHOR_REGISTRY_ADDRESS trong .env.");
    }
    return client;
  }

  private static String hex(byte[] b) {
    return "0x" + java.util.HexFormat.of().formatHex(b);
  }
}
