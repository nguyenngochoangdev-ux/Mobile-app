package vn.ptit.drl.anchor;

import java.util.List;
import java.util.Map;

/**
 * Nguồn bản ghi cần neo của một miền.
 *
 * <h2>Đây là chỗ giữ ranh giới cứng của PROJECT.md §5</h2>
 *
 * <p>Job neo phải gom được dữ liệu từ {@code attendance}, {@code credential}, {@code scoring}
 * — nhưng module {@code anchor} <b>không được import gì từ nghiệp vụ</b>. Giải pháp là đảo
 * chiều phụ thuộc: giao diện này khai ở {@code anchor}, còn <b>các module nghiệp vụ hiện thực
 * nó</b>. {@link AnchorJob} tiêm {@code List<AnchorSource>} và Spring đưa vào mọi bản hiện
 * thực, nên job không biết tên lớp nghiệp vụ nào.
 *
 * <p>Giữ được ranh giới này thì phần đo đạc tuần 7 chỉ là gọi một hàm với N khác nhau; không
 * giữ được thì phải viết lại.
 */
public interface AnchorSource {

  /**
   * Một bản ghi chờ neo.
   *
   * @param sourceId khóa chính ở bảng gốc — {@code anchor_leaves.source_id} lưu lại để tra
   *     ngược proof theo bản ghi
   * @param payload cây giá trị thuần ({@code Map}/{@code List}/{@code String}/{@code Boolean}/
   *     {@code Number}/null) sẵn sàng đưa vào {@link LeafHasher}. <b>Bắt buộc có {@code
   *     nonce}</b>.
   */
  record Item(long sourceId, Map<String, Object> payload) {}

  AnchorDomain domain();

  /** Tên bảng gốc, ghi vào {@code anchor_leaves.source_table}. */
  String sourceTable();

  /**
   * Các bản ghi <b>đã chốt</b> và chưa neo, cũ trước mới sau.
   *
   * <p>"Đã chốt" là điều kiện thật, không phải chi tiết: neo một bản ghi còn có thể thay đổi
   * (ví dụ điểm danh chưa check-out) làm leaf lạc hậu vĩnh viễn, vì contract không cho neo
   * lại. Mỗi bản hiện thực tự định nghĩa mốc chốt của mình và phải ghi rõ lý do.
   */
  List<Item> pending(int limit);

  /**
   * Ghi {@code leaf_hash} xuống bảng gốc sau khi neo xong.
   *
   * <p>Gọi <b>sau</b> khi giao dịch đã lên chuỗi. Ghi trước rồi giao dịch hỏng thì bản ghi bị
   * đánh dấu đã neo trong khi trên chuỗi không có gì — và vì {@link #pending} lọc theo
   * {@code leaf_hash IS NULL}, nó sẽ không bao giờ được neo lại.
   */
  void saveLeafHashes(Map<Long, byte[]> leafHashBySourceId);
}
