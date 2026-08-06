package vn.ptit.drl.credential;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.ptit.drl.anchor.AnchorDomain;
import vn.ptit.drl.anchor.AnchorSource;

/**
 * Nguồn credential cho job neo — miền {@code CRED}.
 *
 * <p>Chiều phụ thuộc: {@code credential} → {@code anchor}, giống
 * {@code AttendanceAnchorSource}. Job neo chỉ biết {@link AnchorSource} và không import lớp
 * nghiệp vụ nào (PROJECT.md §5).
 *
 * <h2>Mốc chốt: được ký là chốt</h2>
 *
 * <p>{@code AttendanceAnchorSource} phải đợi {@code events.end_at} vì bản ghi điểm danh còn
 * thay đổi được (sinh viên chưa check-out). Credential thì không: mọi cột đi vào payload khai
 * {@code updatable = false}, nên ngay khi nó tồn tại là nó đã bất biến. Không có gì để đợi.
 *
 * <h2>Hai khác biệt nữa so với miền ATTEND</h2>
 *
 * <ul>
 *   <li><b>{@code leaf_hash} có sẵn từ lúc cấp</b>, vì chữ ký ký chính nó. Nên không lọc bản
 *       ghi chờ neo theo {@code leaf_hash IS NULL} được — phải hỏi {@code anchor_leaves}.
 *       Xem {@link CredentialRepository#findPendingAnchor}.
 *   <li><b>{@link #saveLeafHashes} không ghi gì.</b> Không phải quên: giá trị mà nó được đưa
 *       cho chính là giá trị đã nằm sẵn trong cột. Thay vì bỏ trống, hàm này <b>đối chiếu</b>
 *       — job vừa tính lại leaf từ payload, và nếu nó khác leaf đã ký thì có gì đó rất sai và
 *       phải vỡ ồn ào.
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CredentialAnchorSource implements AnchorSource {

  private final CredentialRepository repository;

  @Override
  public AnchorDomain domain() {
    return AnchorDomain.CRED;
  }

  @Override
  public String sourceTable() {
    return "credentials";
  }

  @Override
  @Transactional(readOnly = true)
  public List<Item> pending(int limit) {
    List<Credential> rows = repository.findPendingAnchor(PageRequest.of(0, limit));

    List<Item> items = new ArrayList<>(rows.size());
    for (Credential c : rows) {
      // Ném nếu payload dựng lại khác payload đã ký. Thà cả lô CRED không neo được đêm nay
      // còn hơn neo một root cho những leaf khác với leaf mà chữ ký cam kết — cái sau không
      // sửa được, vì AnchorRegistry không cho ghi đè.
      CredentialService.recomputeAndVerifyLeaf(c);
      items.add(new Item(c.getId(), CredentialPayload.of(c)));
    }
    return items;
  }

  /**
   * Đối chiếu, không ghi.
   *
   * <p>{@code credentials.leaf_hash} đã có giá trị từ lúc cấp và khai {@code updatable =
   * false}. Job neo vừa tính lại leaf từ payload một cách độc lập; hai giá trị đó <b>phải</b>
   * bằng nhau. Bằng thì không có gì để làm; khác thì lô này vừa được neo với một root không
   * khớp chữ ký, và đó là thứ phải biết ngay chứ không phải sáu tuần sau.
   */
  @Override
  public void saveLeafHashes(Map<Long, byte[]> leafHashBySourceId) {
    for (Map.Entry<Long, byte[]> e : leafHashBySourceId.entrySet()) {
      repository.findById(e.getKey()).ifPresent(c -> {
        if (!java.util.Arrays.equals(c.getLeafHash(), e.getValue())) {
          throw new IllegalStateException(
              "Credential " + c.getId() + ": job neo tính ra leaf khác leaf đã ký lúc cấp."
                  + " Lô CRED vừa neo KHÔNG khớp chữ ký — kiểm tra ngay CredentialPayload.");
        }
      });
    }
    log.debug("CRED: {} leaf khop chu ky da luu", leafHashBySourceId.size());
  }
}
