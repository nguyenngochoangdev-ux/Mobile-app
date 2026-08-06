package vn.ptit.drl.anchor;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Tra bằng chứng neo của một bản ghi: proof, {@code batchId} trên chuỗi, root, giao dịch.
 *
 * <h2>Vì sao lớp này nằm ở module {@code anchor}</h2>
 *
 * <p>Bundle của sinh viên cần proof, mà proof nằm trong {@code anchor_leaves} —
 * bảng của module {@code anchor}. Không có lớp này thì mỗi module nghiệp vụ muốn xuất bundle
 * lại tự viết SQL trên bảng của {@code anchor}, và lược đồ đó trở thành API công khai không ai
 * tuyên bố. Đổi tên một cột là hỏng ba chỗ không liên quan.
 *
 * <p>Chiều phụ thuộc vẫn đúng PROJECT.md §5: {@code credential} → {@code anchor}. Lớp này
 * nhận {@code (sourceTable, sourceId)} là hai giá trị thuần và <b>không import gì từ nghiệp
 * vụ</b> — nó không biết "credentials" là bảng gì, chỉ biết đó là một chuỗi.
 *
 * <p><i>Ghi chú về chỗ chưa nhất quán:</i> {@code CredentialRepository.findPendingAnchor} vẫn
 * nhắc thẳng tên bảng {@code anchor_leaves} trong một mệnh đề {@code NOT EXISTS}. Chuyển nó
 * qua đây được, nhưng sẽ thành "lấy toàn bộ id đã neo rồi lọc ở tầng ứng dụng" — chậm hơn và
 * tệ hơn khi số credential lớn. Giữ nguyên, có chú thích tại chỗ.
 */
@Service
@RequiredArgsConstructor
public class AnchorProofService {

  private static final HexFormat HEX = HexFormat.of();

  /**
   * Bằng chứng neo của một bản ghi.
   *
   * @param domain miền neo
   * @param batchId {@code batchId} <b>trên chuỗi</b> (quy ước {@code YYYYMMDDnn}) — không phải
   *     khóa chính {@code anchor_batches.id}. Đây là giá trị verifier truyền vào
   *     {@code getRoot(domain, batchId)}.
   * @param leafHash lá của bản ghi này, {@code 0x…}
   * @param proof các sibling hash từ lá lên gốc, {@code 0x…}. <b>Rỗng là hợp lệ</b> — lô một
   *     lá thì root chính là lá (docs/canonicalization.md §8.1).
   * @param merkleRoot root của lô theo CSDL. Verifier <b>phải đọc lại từ chuỗi</b>, không
   *     được tin giá trị này.
   * @param txHash giao dịch neo, {@code null} nếu lô đã dựng cây nhưng chưa lên chuỗi
   * @param blockNumber {@code null} khi chưa lên chuỗi
   * @param anchoredAt {@code null} khi chưa lên chuỗi
   */
  public record Proof(String domain, long batchId, String leafHash, List<String> proof,
                      String merkleRoot, String txHash, Long blockNumber, Instant anchoredAt) {

    /**
     * Lô đã thật sự lên chuỗi chưa.
     *
     * <p>Phân biệt này quan trọng: một lô có proof đầy đủ trong CSDL nhưng {@code txHash} NULL
     * là lô <b>đã dựng cây và chưa gửi giao dịch</b> (xem {@link AnchorJob}). Xuất bundle từ
     * lô đó thì verifier đọc {@code getRoot} về rỗng và báo không xác minh được — đúng, nhưng
     * người dùng sẽ tưởng credential có vấn đề chứ không phải hệ thống chưa neo xong.
     */
    public boolean onChain() {
      return txHash != null && !txHash.isBlank();
    }
  }

  private final JdbcTemplate jdbc;

  /**
   * Bằng chứng neo <b>mới nhất</b> của một bản ghi.
   *
   * <p>{@code Optional.empty()} nghĩa là bản ghi chưa vào lô nào — thường chỉ là chưa tới giờ
   * job neo chạy, không phải lỗi.
   *
   * <p>Lấy lô mới nhất khi có nhiều: ràng buộc {@code uk_leaf_source (source_table, source_id,
   * batch_id)} cho phép cùng một bản ghi nằm ở nhiều lô. Trường hợp đó không xảy ra trong luồng
   * bình thường, nhưng nếu có thì lô mới nhất là lô đáng tin nhất.
   */
  @Transactional(readOnly = true)
  public Optional<Proof> findLatest(String sourceTable, long sourceId) {
    List<Proof> rows = jdbc.query(
        """
        SELECT b.domain, b.batch_id, b.merkle_root, b.tx_hash, b.block_number, b.anchored_at,
               l.leaf_hash, l.proof_json
          FROM anchor_leaves l
          JOIN anchor_batches b ON b.id = l.batch_id
         WHERE l.source_table = ? AND l.source_id = ?
         ORDER BY b.batch_id DESC
         LIMIT 1
        """,
        (rs, rowNum) -> {
          java.sql.Timestamp anchoredAt = rs.getTimestamp("anchored_at");
          Object blockNumber = rs.getObject("block_number");
          return new Proof(
              rs.getString("domain"),
              rs.getLong("batch_id"),
              hex(rs.getBytes("leaf_hash")),
              parseProofJson(rs.getString("proof_json")),
              hex(rs.getBytes("merkle_root")),
              rs.getString("tx_hash"),
              blockNumber == null ? null : ((Number) blockNumber).longValue(),
              anchoredAt == null ? null : anchoredAt.toInstant());
        },
        sourceTable, sourceId);

    return rows.stream().findFirst();
  }

  /**
   * Đọc mảng chuỗi hex từ {@code proof_json}.
   *
   * <p>Viết tay thay vì gọi Jackson, đối xứng với {@link AnchorJob#insertLeaves} vốn tự dựng
   * chuỗi JSON. Nội dung là một mảng chuỗi hex phẳng và không bao giờ khác — nhận một trình
   * phân tích JSON đầy đủ vào đây là mở cửa cho việc ai đó "mở rộng" định dạng proof, thứ mà
   * verifier phía JS không biết đọc.
   *
   * @throws IllegalStateException nếu nội dung không phải mảng hex phẳng
   */
  static List<String> parseProofJson(String json) {
    if (json == null) {
      throw new IllegalStateException("proof_json là NULL — lá này lưu hỏng.");
    }
    String s = json.trim();
    if (!s.startsWith("[") || !s.endsWith("]")) {
      throw new IllegalStateException("proof_json phải là một mảng JSON, nhận được: " + json);
    }
    String body = s.substring(1, s.length() - 1).trim();
    if (body.isEmpty()) {
      // Lô một lá: root chính là lá, proof rỗng. Hợp lệ, không phải lỗi.
      return List.of();
    }

    List<String> out = new java.util.ArrayList<>();
    for (String raw : body.split(",")) {
      String item = raw.trim();
      if (item.length() < 2 || item.charAt(0) != '"' || item.charAt(item.length() - 1) != '"') {
        throw new IllegalStateException(
            "Phần tử proof phải là chuỗi trong nháy kép, nhận được: " + item);
      }
      String value = item.substring(1, item.length() - 1);
      if (!value.matches("^0x[0-9a-f]{64}$")) {
        throw new IllegalStateException(
            "Sibling hash phải là 32 byte hex chữ thường có tiền tố 0x, nhận được: " + value);
      }
      out.add(value);
    }
    return List.copyOf(out);
  }

  private static String hex(byte[] b) {
    return b == null ? null : "0x" + HEX.formatHex(b);
  }
}
