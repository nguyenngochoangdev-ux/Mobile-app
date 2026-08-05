package vn.ptit.drl.anchor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

/**
 * Smoke test ĐỌC contract thật đang chạy trên chuỗi. Không gửi giao dịch, không tốn POL.
 *
 * <p>Chỉ chạy khi có biến môi trường {@code ANCHOR_LIVE_TEST=true} — bộ test thường phải chạy
 * được khi không có mạng và khi `.env` chưa điền khóa. Chạy nó:
 *
 * <pre>
 *   $env:ANCHOR_LIVE_TEST="true"; .\scripts\test-backend.ps1 AnchorRegistryClientLiveTest
 * </pre>
 *
 * <p>Test này bắt đúng lớp lỗi mà unit test không bắt được: sai địa chỉ contract, sai chainId,
 * sai chữ ký hàm ABI, RPC từ chối. Toàn những thứ chỉ lộ ra khi nói chuyện với chuỗi thật.
 */
@EnabledIfEnvironmentVariable(named = "ANCHOR_LIVE_TEST", matches = "true")
class AnchorRegistryClientLiveTest {

  private static Web3j web3j;
  private static AnchorRegistryClient client;

  @BeforeAll
  static void setUp() {
    String rpc = env("AMOY_RPC_URL");
    String address = env("ANCHOR_REGISTRY_ADDRESS");
    String key = env("ANCHOR_PRIVATE_KEY");
    long chainId = Long.parseLong(System.getenv().getOrDefault("CHAIN_ID", "80002"));

    web3j = Web3j.build(new HttpService(rpc));
    client = new AnchorRegistryClient(web3j, address, Credentials.create(key), chainId);
  }

  @AfterAll
  static void tearDown() {
    if (web3j != null) {
      web3j.shutdown();
    }
  }

  private static String env(String name) {
    String v = System.getenv(name);
    if (v == null || v.isBlank()) {
      throw new IllegalStateException(
          "Thiếu biến môi trường " + name + ". Chạy qua scripts/test-backend.ps1 để nạp .env.");
    }
    return v;
  }

  @Test
  @DisplayName("Nối được đúng chuỗi Amoy")
  void noiDungChuoi() throws Exception {
    BigInteger chainId = web3j.ethChainId().send().getChainId();
    assertEquals(BigInteger.valueOf(80002), chainId, "không phải Polygon Amoy");
  }

  @Test
  @DisplayName("Địa chỉ contract có mã — tức là đã deploy thật ở mạng này")
  void contractDaDeploy() throws Exception {
    String code = web3j.ethGetCode(
        env("ANCHOR_REGISTRY_ADDRESS"),
        org.web3j.protocol.core.DefaultBlockParameterName.LATEST).send().getCode();

    assertNotNull(code);
    // "0x" rỗng nghĩa là địa chỉ không có contract — sai địa chỉ, hoặc sai mạng.
    assertTrue(code.length() > 2, "địa chỉ không có bytecode: " + code);
  }

  @Test
  @DisplayName("getRoot lô chưa neo trả về 32 byte 0x00")
  void loChuaNeoTraVeKhong() throws Exception {
    // Chốt luôn việc giải mã ABI đúng: sai chữ ký hàm thì eth_call revert hoặc trả rác.
    byte[] root = client.getRoot(AnchorDomain.ATTEND, 999_999L);

    assertEquals(MerkleService.HASH_BYTES, root.length);
    assertArrayEquals(new byte[MerkleService.HASH_BYTES], root, "lô này lẽ ra chưa neo");
  }

  @Test
  @DisplayName("batchCount đọc được ở cả năm miền")
  void demLoODuMienNeo() throws Exception {
    for (AnchorDomain domain : AnchorDomain.values()) {
      long count = client.batchCount(domain);
      assertTrue(count >= 0, domain + " trả về số âm");
      System.out.printf("  %-8s batchCount = %d%n", domain, count);
    }
  }

  @Test
  @DisplayName("Ví neo có đủ POL để gửi giao dịch")
  void viCoDuPol() throws Exception {
    BigInteger wei = web3j.ethGetBalance(
        client.anchorerAddress(),
        org.web3j.protocol.core.DefaultBlockParameterName.LATEST).send().getBalance();

    double pol = wei.doubleValue() / 1e18;
    System.out.printf("  vi neo %s: %.4f POL%n", client.anchorerAddress(), pol);

    // Một lần neo tốn ~55.000 gas. Ở 60 gwei (đã nhân hệ số 2) là ~0,0033 POL.
    assertTrue(pol > 0.01, "ví neo gần cạn POL: " + pol + " — lấy thêm từ faucet Amoy");
  }
}
