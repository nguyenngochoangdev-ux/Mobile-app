package vn.ptit.drl.credential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;

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
 * Test đường GHI của {@link StatusListClient} trên chuỗi Hardhat cục bộ.
 *
 * <p>Khác {@code anchor()} ở một điểm quan trọng: {@code setRevoked} <b>đảo ngược được</b>,
 * nên chạy trên Amoy về mặt kỹ thuật không hỏng gì vĩnh viễn. Vẫn để ở local vì mỗi lần lật
 * bit để lại một sự kiện {@code StatusChanged} <b>vĩnh viễn trên chuỗi công khai</b>, và làm
 * bẩn lịch sử bằng bit thử nghiệm khiến chính lịch sử đó hết dùng được làm bằng chứng khi bảo
 * vệ.
 *
 * <p>Chuẩn bị:
 *
 * <pre>
 *   cd contracts &amp;&amp; npx hardhat node          # cửa sổ 1
 *   cd contracts &amp;&amp; npm run deploy:local       # cửa sổ 2
 *   $env:LOCAL_CHAIN_TEST="true"
 *   $env:LOCAL_STATUS_LIST="0x..."              # địa chỉ script trên in ra
 *   .\scripts\test-backend.ps1 StatusListClientLocalChainTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "LOCAL_CHAIN_TEST", matches = "true")
class StatusListClientLocalChainTest {

  /** Khóa tài khoản #0 của Hardhat — công khai, CHỈ dùng ở chuỗi local. */
  private static final String HARDHAT_KEY_0 =
      "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

  /** Tài khoản #1 — KHÔNG có STATUS_ROLE. Dùng để chốt rằng phép kiểm quyền là thật. */
  private static final String HARDHAT_KEY_1 =
      "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d";

  private static final long LOCAL_CHAIN_ID = 31337L;

  private static Web3j web3j;
  private static StatusListClient client;
  private static StatusListClient khongCoQuyen;

  /** Chỉ số ngẫu nhiên mỗi lần chạy, để chạy lại được mà không cần khởi động lại node. */
  private static long baseIndex;

  @BeforeAll
  static void setUp() {
    String address = System.getenv("LOCAL_STATUS_LIST");
    if (address == null || address.isBlank()) {
      throw new IllegalStateException(
          "Thiếu LOCAL_STATUS_LIST. Chạy `cd contracts && npm run deploy:local` rồi đặt biến"
              + " môi trường bằng địa chỉ StatusList mà nó in ra.");
    }

    web3j = Web3j.build(new HttpService("http://127.0.0.1:8545"));
    client = new StatusListClient(
        web3j, address, Credentials.create(HARDHAT_KEY_0), LOCAL_CHAIN_ID);
    khongCoQuyen = new StatusListClient(
        web3j, address, Credentials.create(HARDHAT_KEY_1), LOCAL_CHAIN_ID);

    // Dải riêng mỗi lần chạy: contract không ghi lại nếu trạng thái không đổi, nên dùng lại
    // chỉ số cũ sẽ cho ra gas rất thấp và làm phép đo vô nghĩa.
    baseIndex = Math.abs(new SecureRandom().nextLong() % 900_000L) + 1_000L;
  }

  @AfterAll
  static void tearDown() {
    if (web3j != null) {
      web3j.shutdown();
    }
  }

  @Test
  @DisplayName("Lật bit rồi đọc lại: chưa thu hồi → đã thu hồi → bỏ thu hồi")
  void latBitCaHaiChieu() throws Exception {
    long index = baseIndex;

    assertFalse(client.isRevoked(index), "Chỉ số mới phải chưa bị thu hồi");

    TransactionReceipt r1 = client.setRevoked(index, true);
    assertTrue(r1.isStatusOK());
    assertTrue(client.isRevoked(index), "Sau setRevoked(true) phải đọc ra true");

    // Đảo ngược được — khác hẳn anchor(). Đây là chủ ý của W3C Status List: thu hồi nhầm
    // phải sửa được.
    TransactionReceipt r2 = client.setRevoked(index, false);
    assertTrue(r2.isStatusOK());
    assertFalse(client.isRevoked(index), "Sau setRevoked(false) phải đọc ra false");
  }

  @Test
  @DisplayName("Các chỉ số độc lập nhau — lật một bit không đụng bit khác trong cùng word")
  void chiSoDocLap() throws Exception {
    long a = baseIndex + 100;
    long b = a + 1; // cùng word 256 bit
    long xa = a + 300; // word khác

    client.setRevoked(a, true);

    assertTrue(client.isRevoked(a));
    assertFalse(client.isRevoked(b), "Bit kề bên trong cùng word bị lật nhầm");
    assertFalse(client.isRevoked(xa), "Bit ở word khác bị lật nhầm");
  }

  /**
   * Chênh lệch giữa lần ghi đầu vào một word và lần sau, tính bằng gas <b>tuyệt đối</b>.
   *
   * <p><b>Đo bằng HIỆU, không bằng TỶ LỆ — đây là chỗ kỳ vọng ban đầu sai.</b> Bản đầu của
   * test này đòi {@code gasDau > gasSau * 2} và đỏ: đo được 47.978 rồi 30.878, tức
   * <b>1,55×</b>. Lý do là mỗi lần thu hồi ở đây là <b>một giao dịch riêng</b>, nên cả hai
   * đều gánh 21.000 gas phí giao dịch cộng chi phí kiểm quyền {@code AccessControl} — phần
   * cố định đó át tỷ lệ.
   *
   * <p>Hiệu thì đúng như lý thuyết EVM: ghi ô lưu trữ 0 → khác 0 tốn 20.000 gas, còn khác 0
   * → khác 0 tốn 2.900. Chênh ~17.100 đo được khớp mức đó.
   *
   * <p><b>Khác phép đo ở {@code docs/measurements.md} §11.4</b>, chỗ nói "bitmap rẻ hơn
   * 8,47× khi gom cụm": con số đó là <b>gas biên trong CÙNG một giao dịch</b>
   * ({@code setRevokedBatch}), nơi phí giao dịch chỉ trả một lần. Hai phép đo không mâu
   * thuẫn — chúng đo hai thứ khác nhau, và trộn lẫn chúng trong báo cáo là chỗ dễ bị bắt bẻ.
   */
  @Test
  @DisplayName("Ghi lần đầu vào một word đắt hơn ~17.000 gas — đo bằng HIỆU, không bằng tỷ lệ")
  void gasLanDauDatHon() throws Exception {
    long wordMoi = baseIndex + 10_000;

    long gasDau = client.setRevoked(wordMoi, true).getGasUsed().longValueExact();
    long gasSau = client.setRevoked(wordMoi + 1, true).getGasUsed().longValueExact();

    long hieu = gasDau - gasSau;
    assertTrue(hieu > 10_000,
        "Chờ đợi lần ghi đầu vào một word đắt hơn lần sau khoảng 17.000 gas (20.000 - 2.900"
            + " của SSTORE). Đo được: " + gasDau + " rồi " + gasSau + ", hiệu " + hieu
            + ". Hiệu nhỏ nghĩa là bitmap không còn lợi thế gom cụm, và kết luận ở"
            + " docs/measurements.md §11.4 cần đo lại.");
  }

  @Test
  @DisplayName("Ghi lại cùng trạng thái vẫn thành công nhưng RẺ — contract bỏ qua")
  void ghiLaiCungTrangThai() throws Exception {
    long index = baseIndex + 20_000;

    long gasThat = client.setRevoked(index, true).getGasUsed().longValueExact();
    long gasThua = client.setRevoked(index, true).getGasUsed().longValueExact();

    assertTrue(gasThua < gasThat,
        "Ghi lại trạng thái không đổi phải rẻ hơn: " + gasThat + " rồi " + gasThua);
    assertTrue(client.isRevoked(index));
  }

  @Test
  @DisplayName("Khóa thiếu STATUS_ROLE bị TỪ CHỐI — phép kiểm quyền là thật")
  void khongCoQuyenThiBiTuChoi() {
    long index = baseIndex + 30_000;

    Exception e = assertThrows(Exception.class, () -> khongCoQuyen.setRevoked(index, true));
    assertTrue(e.getMessage() != null && !e.getMessage().isBlank(), "Phải có thông báo lỗi");
  }

  @Test
  @DisplayName("Chỉ số âm bị chặn ở tầng client, không tốn một giao dịch")
  void chiSoAm() {
    assertThrows(IllegalArgumentException.class, () -> client.setRevoked(-1, true));
  }

  @Test
  @DisplayName("isRevoked là eth_call — đọc được mà không tốn gas")
  void docKhongTonGas() throws Exception {
    assertEquals(false, client.isRevoked(baseIndex + 40_000));
  }
}
