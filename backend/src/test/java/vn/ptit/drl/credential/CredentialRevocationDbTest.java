package vn.ptit.drl.credential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import vn.ptit.drl.common.web.NotFoundException;
import vn.ptit.drl.identity.Student;
import vn.ptit.drl.identity.StudentRepository;
import vn.ptit.drl.org.OrgType;
import vn.ptit.drl.org.Organization;
import vn.ptit.drl.org.OrganizationRepository;

/**
 * Luồng thu hồi đầy đủ — CSDL thật + chuỗi Hardhat cục bộ thật, không mock gì.
 *
 * <p>Chuẩn bị:
 *
 * <pre>
 *   cd contracts &amp;&amp; npx hardhat node          # cửa sổ 1
 *   cd contracts &amp;&amp; npm run deploy:local       # cửa sổ 2
 *   $env:LOCAL_CHAIN_TEST="true"
 *   $env:LOCAL_STATUS_LIST="0x..."
 *   .\scripts\test-backend.ps1 CredentialRevocationDbTest
 * </pre>
 *
 * <p><b>Lưu ý về việc cuộn giao dịch:</b> phần CSDL cuộn lại được, phần <b>chuỗi thì
 * không</b>. Bit đã lật vẫn lật sau khi test kết thúc. Không sao — chuỗi local vứt đi được,
 * và {@code npx hardhat node} khởi động lại là sạch trơn. Đây cũng chính là lý do lớp này
 * không chạy trên Amoy.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "LOCAL_CHAIN_TEST", matches = "true")
@TestPropertySource(properties = {
    "drl.credential.issuer-private-key="
        + "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80",
    "drl.anchor.enabled=true",
    "drl.anchor.rpc-url=http://127.0.0.1:8545",
    "drl.anchor.chain-id=31337",
    "drl.anchor.private-key="
        + "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80",
    "drl.anchor.status-list-address=${LOCAL_STATUS_LIST}",
    // AnchorChainConfig đòi địa chỉ này để dựng bean; thu hồi không dùng tới nó.
    "drl.anchor.anchor-registry-address=0x5FbDB2315678afecb367f032d93F642f64180aa3",
    // Pool nhỏ để chỉ số nằm gọn, không ảnh hưởng phép kiểm nào.
    "drl.credential.status-list-pool-size=1048576"
})
class CredentialRevocationDbTest {

  @Autowired CredentialService service;
  @Autowired CredentialRevocationService revocation;
  @Autowired CredentialRepository repository;
  @Autowired StatusListClient client;
  @Autowired StudentRepository students;
  @Autowired OrganizationRepository organizations;
  @Autowired JdbcTemplate jdbc;

  /**
   * Một {@code users.id} có thật, hoặc {@code null}.
   *
   * <p>{@code audit_logs.actor_id} có khóa ngoại tới {@code users} nên không bịa số được —
   * và thu hồi giờ ghi nhật ký, nên mọi lời gọi ở đây đều đi qua ràng buộc đó.
   */
  private Long actorCoThat() {
    java.util.List<Long> ids = jdbc.queryForList("SELECT id FROM users LIMIT 1", Long.class);
    return ids.isEmpty() ? null : ids.get(0);
  }

  private Credential capCredential() {
    Organization org = organizations.save(Organization.builder()
        .name("Doan Thanh nien — test thu hoi")
        .type(OrgType.DOAN)
        .build());

    Student student = students.save(Student.builder()
        .mssv("REV" + System.nanoTime() % 100000000L)
        .fullName("Nguyễn Ngọc Hoàng")
        .build());

    return service.issue(new CredentialService.Request(student, org, "2026-1", 5, 30, null));
  }

  // ------------------------------------------------------------------ luồng chính

  @Test
  @DisplayName("Thu hồi: bit lật trên chuỗi VÀ revoked_at được ghi")
  void thuHoi() throws Exception {
    Credential c = capCredential();
    long index = c.getStatusListIndex();

    assertFalse(client.isRevoked(index), "Credential mới cấp phải chưa bị thu hồi");
    assertNull(c.getRevokedAt());

    var kq = revocation.setRevoked(c.getId(), true, "Cap nham hoc ky", actorCoThat());

    assertTrue(kq.revoked());
    assertFalse(kq.daDungTruocDo());
    assertNotNull(kq.txHash());
    assertTrue(kq.gasUsed() > 0);
    assertEquals(index, kq.statusListIndex());

    // Nguồn sự thật là CHUỖI — kiểm nó trước.
    assertTrue(client.isRevoked(index), "Bit trên chuỗi chưa bật");

    // Rồi mới tới CSDL.
    Object revokedAt = jdbc.queryForMap(
        "SELECT revoked_at, revoke_tx_hash FROM credentials WHERE id = ?", c.getId())
        .get("revoked_at");
    assertNotNull(revokedAt, "revoked_at chưa được ghi");
  }

  @Test
  @DisplayName("Bỏ thu hồi: đảo ngược được — khác hẳn anchor()")
  void boThuHoi() throws Exception {
    Credential c = capCredential();
    long index = c.getStatusListIndex();

    revocation.setRevoked(c.getId(), true, null, actorCoThat());
    assertTrue(client.isRevoked(index));

    var kq = revocation.setRevoked(c.getId(), false, "Thu hoi nham", actorCoThat());

    assertFalse(kq.revoked());
    assertFalse(client.isRevoked(index), "Bit phải tắt lại trên chuỗi");

    var row = jdbc.queryForMap(
        "SELECT revoked_at, revoke_tx_hash FROM credentials WHERE id = ?", c.getId());
    assertNull(row.get("revoked_at"), "revoked_at phải về NULL");
    assertNull(row.get("revoke_tx_hash"));
  }

  @Test
  @DisplayName("Gọi lại khi trạng thái đã đúng: KHÔNG gửi giao dịch, chỉ đồng bộ CSDL")
  void goiLaiKhongGuiGiaoDich() {
    Credential c = capCredential();

    var lan1 = revocation.setRevoked(c.getId(), true, null, actorCoThat());
    assertNotNull(lan1.txHash());

    var lan2 = revocation.setRevoked(c.getId(), true, null, actorCoThat());

    assertTrue(lan2.daDungTruocDo(), "Phải nhận ra trạng thái đã đúng");
    assertNull(lan2.txHash(), "Không được gửi giao dịch thừa");
    assertEquals(0L, lan2.gasUsed());
  }

  // ------------------------------------------------------------------ đồng bộ lệch

  @Test
  @DisplayName("reconcile: chuỗi đã thu hồi mà CSDL chưa biết → lấy theo CHUỖI")
  void reconcileLayTheoChuoi() throws Exception {
    Credential c = capCredential();
    long index = c.getStatusListIndex();

    // Mô phỏng đúng cách hỏng mà thứ tự "chuỗi trước" cố ý chọn: giao dịch đã lên chuỗi,
    // bước ghi CSDL hỏng. Lật bit thẳng bằng client rồi để CSDL nguyên.
    client.setRevoked(index, true);
    assertTrue(client.isRevoked(index));
    assertNull(jdbc.queryForMap(
        "SELECT revoked_at FROM credentials WHERE id = ?", c.getId()).get("revoked_at"));

    boolean trenChuoi = revocation.reconcile(c.getId());

    assertTrue(trenChuoi);
    assertNotNull(jdbc.queryForMap(
        "SELECT revoked_at FROM credentials WHERE id = ?", c.getId()).get("revoked_at"),
        "reconcile phải ghi revoked_at cho khớp chuỗi");
  }

  @Test
  @DisplayName("reconcile: CSDL bảo đã thu hồi mà chuỗi thì chưa → cũng lấy theo CHUỖI")
  void reconcileXoaTrangThaiSai() {
    Credential c = capCredential();

    // Cách hỏng NGUY HIỂM nhất — trang quản trị báo "đã thu hồi" trong khi nhà tuyển dụng
    // chạy verifier vẫn thấy credential còn hiệu lực. Thứ tự chuỗi-trước sinh ra để tránh
    // tình huống này; reconcile là đường sửa nếu nó vẫn xảy ra bằng cách nào đó.
    jdbc.update("UPDATE credentials SET revoked_at = NOW(3) WHERE id = ?", c.getId());

    boolean trenChuoi = revocation.reconcile(c.getId());

    assertFalse(trenChuoi);
    assertNull(jdbc.queryForMap(
        "SELECT revoked_at FROM credentials WHERE id = ?", c.getId()).get("revoked_at"),
        "reconcile phải XÓA revoked_at vì trên chuỗi chưa thu hồi");
  }

  // ------------------------------------------------------------------ từ chối

  @Test
  @DisplayName("Credential không tồn tại")
  void khongCoCredential() {
    assertThrows(NotFoundException.class, () -> revocation.setRevoked(999_999_999L, true, null, null));
  }

  @Test
  @DisplayName("Thu hồi xong thì bundle vẫn xuất được — chỉ verifier báo ĐÃ THU HỒI")
  void bundleVanXuatDuoc() {
    // Điểm cần chốt: thu hồi KHÔNG làm hỏng bằng chứng. Leaf, chữ ký, proof vẫn nguyên; chỉ
    // có bit trạng thái đổi. Người cầm bundle vẫn chứng minh được credential từng tồn tại và
    // do ai cấp — họ chỉ không dùng nó được nữa.
    Credential c = capCredential();
    revocation.setRevoked(c.getId(), true, null, actorCoThat());

    Credential doclai = repository.findById(c.getId()).orElseThrow();
    assertEquals(c.getPayloadJson(), doclai.getPayloadJson(), "payload không được đổi");
    assertArrayEqualsHelper(c.getLeafHash(), doclai.getLeafHash());
    assertArrayEqualsHelper(c.getSignature(), doclai.getSignature());
  }

  private static void assertArrayEqualsHelper(byte[] a, byte[] b) {
    assertTrue(java.util.Arrays.equals(a, b), "Thu hồi đã làm đổi bằng chứng — sai nghiêm trọng");
  }
}
