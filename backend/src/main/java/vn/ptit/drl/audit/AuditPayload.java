package vn.ptit.drl.audit;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dựng payload miền {@code AUDIT} từ một bản ghi nhật ký.
 *
 * <p><b>HỢP ĐỒNG giữa backend và verifier.</b> Nửa JS: {@code verifier/src/audit.mjs}.
 * Vector: {@code canonical-vectors.json}, tiền tố {@code audit-payload}.
 * <b>Sửa lớp này phải đi kèm {@code /canonical-hash}.</b>
 *
 * <h2>Mười một trường — và trường quan trọng nhất là {@code prevHash}</h2>
 *
 * <p>Neo {@code hash} một mình chỉ chứng minh <i>bản ghi này</i> đã tồn tại. Neo cả
 * {@code prevHash} chứng minh <b>vị trí của nó trong chuỗi</b> — tức là chứng minh không có
 * bản ghi nào bị chèn vào hay gỡ ra trước nó. Đó mới là thứ luận điểm 1 hứa.
 *
 * <p>Hệ quả có lợi và đáng nêu trong báo cáo: người kiểm toán cầm hai bản ghi liên tiếp
 * <b>đã neo</b> có thể tự tính lại {@code hash} từ payload và đối chiếu với {@code prevHash}
 * của bản ghi sau — <b>không cần tin CSDL của trường một chút nào</b>.
 *
 * <h2>Vì sao KHÔNG neo {@code before}/{@code after}</h2>
 *
 * <p>Payload chỉ mang {@code beforeHash}/{@code afterHash}. Hai lý do, cả hai đều thật:
 *
 * <ul>
 *   <li><b>Riêng tư.</b> Hai cột đó chứa dữ liệu cá nhân thật (điểm, họ tên, thiết bị). Lá
 *       Merkle của một bản ghi xuất hiện trong proof của <b>bản ghi khác</b> — nếu payload
 *       mang nội dung thì mọi proof đều là một cửa sổ nhìn vào dữ liệu người khác. Nonce đã
 *       chặn việc vét cạn, nhưng cách chắc chắn hơn là <b>không đưa nội dung vào từ đầu</b>.
 *   <li><b>Bền vững.</b> JSON tuỳ ý không đi qua {@code Jcs} được — xem {@link AuditHasher}
 *       quyết định 2.
 * </ul>
 *
 * <p>Cam kết về nội dung <b>không</b> mất đi: {@code beforeHash} là keccak của chính byte
 * đó, nên đổi một ký tự là đổi hash là đứt xích.
 */
public final class AuditPayload {

  private static final HexFormat HEX = HexFormat.of();

  private AuditPayload() {}

  /**
   * Dựng payload chuẩn tắc.
   *
   * @throws IllegalStateException nếu bản ghi thiếu dữ liệu bắt buộc để neo
   */
  public static Map<String, Object> of(AuditLog e) {
    if (e.getNonce() == null || e.getNonce().length != 16) {
      throw new IllegalStateException(
          "Bản ghi nhật ký " + e.getId() + " thiếu nonce 16 byte — không neo được."
              + " Xem PROJECT.md §2.3.");
    }
    if (e.getHash() == null || e.getHash().length != 32) {
      throw new IllegalStateException(
          "Bản ghi nhật ký " + e.getId() + " thiếu mắt xích `hash` — chuỗi băm hỏng.");
    }

    Map<String, Object> p = new LinkedHashMap<>();
    p.put("seq", e.getId());
    p.put("action", e.getAction());
    p.put("entity", e.getEntity());
    p.put("entityId", e.getEntityId());
    p.put("actorId", e.getActorId());
    p.put("at", AuditHasher.isoSeconds(e.getCreatedAt()));
    p.put("beforeHash", AuditHasher.hashOfJson(e.getBeforeJson()));
    p.put("afterHash", AuditHasher.hashOfJson(e.getAfterJson()));
    // prevHash NULL ở bản ghi đầu tiên GIỮ NGUYÊN null trong payload — không thay bằng 32
    // byte 0x00 như lúc băm mắt xích. Hai chỗ hai quy ước, và đó là chủ ý: ở payload, `null`
    // nói đúng sự thật "đây là bản ghi đầu chuỗi", còn một chuỗi 64 số 0 trông như một hash
    // thật và sẽ bị người đọc hiểu nhầm.
    p.put("prevHash", e.getPrevHash() == null ? null : "0x" + HEX.formatHex(e.getPrevHash()));
    p.put("hash", "0x" + HEX.formatHex(e.getHash()));
    p.put("nonce", "0x" + HEX.formatHex(e.getNonce()));
    return p;
  }
}
