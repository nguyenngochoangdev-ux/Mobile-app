package vn.ptit.drl.anchor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

/**
 * Test đường GHI trên chuỗi Hardhat cục bộ — gửi giao dịch {@code anchor()} thật.
 *
 * <p>Không chạy trên Amoy vì {@code anchor()} là <b>không thể hoàn tác</b>: contract cố ý
 * không cho ghi đè, nên mỗi {@code (domain, batchId)} chỉ dùng được đúng một lần. Thử ở đây
 * là cách duy nhất gỡ lỗi đường ghi mà không đốt vĩnh viễn một batchId thật.
 *
 * <p>Chuẩn bị:
 *
 * <pre>
 *   cd contracts &amp;&amp; npx hardhat node          # cửa sổ 1
 *   cd contracts &amp;&amp; npm run deploy:local       # cửa sổ 2
 *   $env:LOCAL_CHAIN_TEST="true"
 *   $env:LOCAL_ANCHOR_REGISTRY="0x5FbDB..."     # địa chỉ script trên in ra
 *   .\scripts\test-backend.ps1 AnchorRegistryClientLocalChainTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "LOCAL_CHAIN_TEST", matches = "true")
class AnchorRegistryClientLocalChainTest {

  /** Khóa tài khoản #0 của Hardhat — công khai, ai cũng biết, CHỈ dùng ở chuỗi local. */
  private static final String HARDHAT_KEY_0 =
      "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

  private static final long LOCAL_CHAIN_ID = 31337L;

  private static Web3j web3j;
  private static AnchorRegistryClient client;

  /** Mỗi lần chạy dùng dải batchId riêng, để chạy lại được mà không cần khởi động lại node. */
  private static long batchBase;

  @BeforeAll
  static void setUp() {
    String rpc = System.getenv().getOrDefault("LOCAL_RPC_URL", "http://127.0.0.1:8545");
    String address = System.getenv("LOCAL_ANCHOR_REGISTRY");
    if (address == null || address.isBlank()) {
      throw new IllegalStateException(
          "Thiếu LOCAL_ANCHOR_REGISTRY. Chạy `cd contracts && npm run deploy:local` rồi lấy"
              + " địa chỉ AnchorRegistry nó in ra.");
    }

    web3j = Web3j.build(new HttpService(rpc));
    client = new AnchorRegistryClient(
        web3j, address, Credentials.create(HARDHAT_KEY_0), LOCAL_CHAIN_ID);
    batchBase = System.currentTimeMillis() / 1000L;
  }

  @AfterAll
  static void tearDown() {
    if (web3j != null) {
      web3j.shutdown();
    }
  }

  /** Lá thật, đi qua đúng đường LeafHasher mà hệ thống dùng. */
  private static byte[] leaf(int i) {
    return LeafHasher.leaf(
        AnchorDomain.ATTEND,
        Map.of(
            "studentCode", "B21DCCN" + String.format("%03d", i),
            "eventId", 128,
            "checkInAt", "2026-08-06T02:00:00Z",
            "verified", true,
            "nonce", LeafHasher.newNonce()));
  }

  @Test
  @DisplayName("Neo được root thật và đọc lại đúng nguyên vẹn")
  void neoVaDocLai() throws Exception {
    List<byte[]> leaves = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      leaves.add(leaf(i));
    }
    byte[] root = MerkleService.root(leaves);
    long batchId = batchBase + 1;

    // Trước khi neo: chưa có gì.
    assertArrayEquals(new byte[32], client.getRoot(AnchorDomain.ATTEND, batchId));

    TransactionReceipt receipt =
        client.anchor(AnchorDomain.ATTEND, batchId, root, leaves.size());

    assertTrue(receipt.isStatusOK());
    System.out.printf(
        "  tx %s · gas %s · lo %d · %d la%n",
        receipt.getTransactionHash(), receipt.getGasUsed(), batchId, leaves.size());

    // Sau khi neo: đọc lại đúng root đã gửi. Đây là vòng khép kín Java -> chuỗi -> Java.
    assertArrayEquals(root, client.getRoot(AnchorDomain.ATTEND, batchId));
  }

  @Test
  @DisplayName("Proof của một lá verify được về ĐÚNG root đang nằm trên chuỗi")
  void proofVerifyVeRootTrenChuoi() throws Exception {
    // Đây là bài test có ý nghĩa nhất của cả lớp: nó khép kín toàn bộ chuỗi lập luận của
    // đề tài — bản ghi -> leaf -> cây Merkle -> root trên chuỗi -> proof -> xác minh lại.
    List<byte[]> leaves = new ArrayList<>();
    for (int i = 0; i < 17; i++) { // số lẻ: có nút bị đẩy lên
      leaves.add(leaf(100 + i));
    }
    byte[] root = MerkleService.root(leaves);
    long batchId = batchBase + 2;

    client.anchor(AnchorDomain.ATTEND, batchId, root, leaves.size());
    byte[] onChainRoot = client.getRoot(AnchorDomain.ATTEND, batchId);

    for (int i = 0; i < leaves.size(); i++) {
      MerkleService.Proof proof = MerkleService.proof(leaves, i);
      assertTrue(
          MerkleService.verify(leaves.get(i), proof.siblings(), onChainRoot),
          "lá " + i + " không verify được về root trên chuỗi");
    }
  }

  @Test
  @DisplayName("batchCount tăng sau mỗi lần neo")
  void demLoTang() throws Exception {
    long before = client.batchCount(AnchorDomain.SCORE);

    client.anchor(AnchorDomain.SCORE, batchBase + 3, MerkleService.root(List.of(leaf(1))), 1);

    assertEquals(before + 1, client.batchCount(AnchorDomain.SCORE));
  }

  @Test
  @DisplayName("Neo lại cùng một lô bị REVERT — bất biến giữ được qua web3j")
  void khongNeoDeDuoc() throws Exception {
    // Test Hardhat đã chốt điều này ở tầng Solidity. Ở đây chốt lại qua đúng đường mà
    // backend đi, để chắc lỗi revert không bị nuốt mất ở tầng web3j.
    long batchId = batchBase + 4;
    byte[] root1 = MerkleService.root(List.of(leaf(1), leaf(2)));
    byte[] root2 = MerkleService.root(List.of(leaf(3), leaf(4)));

    client.anchor(AnchorDomain.CRED, batchId, root1, 2);

    var e = assertThrows(
        Exception.class, () -> client.anchor(AnchorDomain.CRED, batchId, root2, 2));
    assertTrue(
        e.getMessage() != null && (e.getMessage().contains("revert")
            || e.getMessage().contains("đã neo")),
        "thông báo lỗi không nói rõ nguyên nhân: " + e.getMessage());

    // Và quan trọng nhất: root cũ KHÔNG bị thay đổi.
    assertArrayEquals(root1, client.getRoot(AnchorDomain.CRED, batchId));
  }

  @Test
  @DisplayName("Đầu vào sai bị chặn TRƯỚC khi tốn gas")
  void chanDauVaoSaiTruocKhiGuiTx() {
    assertThrows(
        IllegalArgumentException.class,
        () -> client.anchor(AnchorDomain.ATTEND, 1L, new byte[31], 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> client.anchor(AnchorDomain.ATTEND, 1L, new byte[32], 0));
  }
}
