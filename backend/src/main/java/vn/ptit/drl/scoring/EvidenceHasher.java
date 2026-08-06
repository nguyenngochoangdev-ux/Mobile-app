package vn.ptit.drl.scoring;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.web3j.crypto.Hash;

import vn.ptit.drl.anchor.Jcs;

/**
 * Công thức {@code evidence_hash} — nửa Java. Nửa JS: {@code verifier/src/score.mjs}.
 *
 * <pre>
 *   evidenceHash = keccak256( UTF-8( JCS( {"domain":"ATTEND","leaves":[...đã sắp xếp...]} ) ) )
 * </pre>
 *
 * <h2>Nó chứng minh điều gì — và vì sao đây là phần đáng viết nhất của báo cáo</h2>
 *
 * <p>Một điểm rèn luyện là con số cuối cùng của một phép tính. Câu hỏi mà mọi hệ thống chấm
 * điểm đều né là: <b>phép tính đó chạy trên dữ liệu nào?</b> Không trả lời được thì "chấm tự
 * động" chỉ là chuyển việc tin cán bộ sang tin máy chủ.
 *
 * <p>{@code evidence_hash} trả lời được. Nó cam kết vào <b>đúng tập bản ghi điểm danh</b> đã
 * dùng, qua chính leaf hash của chúng ở miền {@code ATTEND}. Ghép với {@code rulesetHash}
 * (bộ quy tắc nào) trong cùng payload {@code SCORE}, một điểm số trở thành <b>tái tính lại
 * được bởi người ngoài</b>:
 *
 * <ol>
 *   <li>sinh viên có bản ghi điểm danh của mình và nonce của chúng ⇒ tính ra được các leaf;
 *   <li>sắp xếp, băm ⇒ ra {@code evidenceHash}; so với giá trị đã neo;
 *   <li>tải bộ quy tắc công khai, đối chiếu {@code rulesetHash} đã neo;
 *   <li>chạy lại phép tính ⇒ phải ra đúng con số đã neo.
 *
 * </ol>
 *
 * <p>Bốn bước đó <b>không cần máy chủ của trường</b> ở bước nào. Đó là "verifiable computation
 * bản nhẹ" mà {@code V1__init.sql} nói tới, và là khác biệt rõ nhất so với EduCTX — công trình
 * đó neo <i>kết quả</i>, không neo <i>đầu vào của phép tính ra kết quả</i>.
 *
 * <h2>Vì sao SẮP XẾP, và vì sao dùng leaf chứ không phải id</h2>
 *
 * <p><b>Sắp xếp</b> vì thứ tự duyệt bản ghi là chi tiết của truy vấn CSDL. Không sắp thì đổi
 * một mệnh đề {@code ORDER BY} là đổi mọi {@code evidence_hash}, dù dữ liệu y hệt.
 *
 * <p><b>Leaf chứ không phải id</b> vì id chỉ có nghĩa bên trong CSDL của trường — người ngoài
 * không kiểm được. Leaf thì đã nằm trong cây Merkle của lô {@code ATTEND} đã neo, nên mỗi
 * phần tử của bằng chứng <b>tự nó cũng chứng minh được</b>.
 *
 * <h2>Giới hạn phải nói ra</h2>
 *
 * <p>{@code evidence_hash} chứng minh <b>đã dùng đúng những bản ghi này</b>. Nó <b>không</b>
 * chứng minh <i>không bỏ sót</i> bản ghi nào: nếu hệ thống lặng lẽ bỏ qua một bản ghi hợp lệ
 * thì bằng chứng vẫn khớp với tập đã dùng. Chống bỏ sót là việc của phép so số lượng, không
 * phải của hàm băm. Ghi vào phần hạn chế.
 */
public final class EvidenceHasher {

  private static final HexFormat HEX = HexFormat.of();

  private EvidenceHasher() {}

  /**
   * Cây giá trị được băm. Tách riêng để test vector đối chiếu từng byte.
   *
   * @param leavesHex leaf hash miền {@code ATTEND}, dạng {@code 0x…} chữ thường; thứ tự tuỳ ý
   *     vì hàm này tự sắp xếp
   */
  public static Map<String, Object> evidence(List<String> leavesHex) {
    List<String> sorted = new ArrayList<>(leavesHex);
    sorted.sort(String::compareTo);

    for (String h : sorted) {
      if (h == null || !h.matches("^0x[0-9a-f]{64}$")) {
        throw new IllegalArgumentException(
            "Leaf phải là 32 byte hex chữ thường có tiền tố 0x, nhận được: " + h);
      }
    }
    // Lá trùng nghĩa là một bản ghi được đếm hai lần — điểm sẽ sai, và bằng chứng sẽ nói dối
    // rằng nó đúng. Vỡ ồn ào. Cùng lý do MerkleService từ chối lá trùng.
    if (sorted.stream().distinct().count() != sorted.size()) {
      throw new IllegalArgumentException(
          "Danh sách bằng chứng có lá TRÙNG — một bản ghi điểm danh bị đếm hai lần.");
    }

    Map<String, Object> e = new LinkedHashMap<>();
    e.put("domain", "ATTEND");
    e.put("leaves", List.copyOf(sorted));
    return e;
  }

  /**
   * {@code evidence_hash} dạng 32 byte thô.
   *
   * <p>Danh sách rỗng <b>hợp lệ</b> và có ý nghĩa riêng: sinh viên không tham gia hoạt động
   * nào. Điểm của họ khi đó hoàn toàn đến từ điểm nền mặc định của bộ quy tắc, và bằng chứng
   * nói đúng điều đó — băm của một danh sách rỗng vẫn là một cam kết kiểm được.
   */
  public static byte[] hash(List<String> leavesHex) {
    return Hash.sha3(
        Jcs.canonicalize(evidence(leavesHex)).getBytes(StandardCharsets.UTF_8));
  }

  /** {@code evidence_hash} dạng {@code 0x…} — dùng để log và cho payload. */
  public static String hashHex(List<String> leavesHex) {
    return "0x" + HEX.formatHex(hash(leavesHex));
  }
}
