package vn.ptit.drl.credential;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

/**
 * Gọi contract {@code StatusList} trên Polygon Amoy — bật/tắt bit thu hồi credential.
 *
 * <p>Viết tay thay vì sinh wrapper bằng web3j codegen, cùng lý do với
 * {@code AnchorRegistryClient}: contract có đúng hai hàm cần dùng, và thêm một bước codegen
 * vào build là thêm một thứ hỏng được ngay trước buổi bảo vệ.
 *
 * <h2>Vì sao lớp này ở {@code credential} chứ không ở {@code anchor}</h2>
 *
 * <p>{@code AnchorRegistryClient} nằm ở {@code anchor} vì neo Merkle là <b>việc của module
 * đó</b> và nó không biết gì về nghiệp vụ. {@code StatusList} thì ngược lại: nó tồn tại
 * <b>chỉ để</b> thu hồi credential, và chỉ số nó nhận là {@code credentials.status_list_index}.
 * Đặt nó ở {@code anchor} sẽ làm module đó biết tới một khái niệm nghiệp vụ, đúng thứ
 * PROJECT.md §5 cấm.
 *
 * <p>Phụ thuộc duy nhất đi ra ngoài là {@link Web3j} — một kiểu của thư viện, không phải kiểu
 * nghiệp vụ. Bean đó do {@code AnchorChainConfig} dựng, nên thu hồi chỉ chạy được khi
 * {@code drl.anchor.enabled=true}. Đúng: không có chuỗi thì không thu hồi được, và giả vờ
 * thu hồi được bằng cách chỉ ghi CSDL là cách hỏng tệ nhất (xem
 * {@link CredentialRevocationService}).
 */
public class StatusListClient {

  /** Đo được ~48.500 gas cho một lần lật bit (docs/measurements.md §11.4). Biên rộng. */
  private static final BigInteger GAS_LIMIT = BigInteger.valueOf(120_000);

  /** Amoy có phí ưu tiên tối thiểu; nhân thêm để tx không nằm chờ giữa buổi demo. */
  private static final BigInteger GAS_PRICE_MULTIPLIER = BigInteger.valueOf(2);

  private final Web3j web3j;
  private final String contractAddress;
  private final Credentials credentials;
  private final RawTransactionManager txManager;
  private final PollingTransactionReceiptProcessor receiptProcessor;

  public StatusListClient(Web3j web3j, String contractAddress, Credentials credentials,
                          long chainId) {
    this.web3j = web3j;
    this.contractAddress = contractAddress;
    this.credentials = credentials;
    this.receiptProcessor = new PollingTransactionReceiptProcessor(web3j, 5_000L, 60);
    this.txManager = new RawTransactionManager(web3j, credentials, chainId, receiptProcessor);
  }

  public String signerAddress() {
    return credentials.getAddress();
  }

  // ---------------------------------------------------------------- đọc

  /**
   * Bit tại {@code index} đã bật chưa — {@code eth_call}, không cần khóa.
   *
   * <p>Đây đúng là phép đọc mà verifier tĩnh chạy. Backend gọi lại nó để <b>xác nhận</b> giao
   * dịch đã có hiệu lực thật trước khi ghi CSDL, thay vì tin vào biên nhận.
   */
  public boolean isRevoked(long index) throws IOException {
    Function fn = new Function(
        "isRevoked",
        List.of(new Uint256(BigInteger.valueOf(index))),
        List.of(new TypeReference<Bool>() {}));

    EthCall response = web3j.ethCall(
        Transaction.createEthCallTransaction(
            credentials.getAddress(), contractAddress, FunctionEncoder.encode(fn)),
        DefaultBlockParameterName.LATEST).send();

    if (response.hasError()) {
      throw new IOException("eth_call thất bại: " + response.getError().getMessage());
    }
    List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), fn.getOutputParameters());
    if (decoded.isEmpty()) {
      throw new IOException(
          "eth_call trả về rỗng — sai địa chỉ contract, hay contract chưa deploy ở mạng này?");
    }
    return (Boolean) decoded.get(0).getValue();
  }

  // ---------------------------------------------------------------- ghi

  /**
   * Bật hoặc tắt bit thu hồi.
   *
   * <p><b>Khác {@code anchor()}: thao tác này ĐẢO NGƯỢC ĐƯỢC.</b> {@code setRevoked(i, false)}
   * bỏ thu hồi. Đó là chủ ý của W3C Status List — thu hồi nhầm phải sửa được, khác hẳn với
   * root đã neo.
   *
   * <p>Nhưng <b>lịch sử thì không xóa được</b>: mỗi lần lật phát ra sự kiện
   * {@code StatusChanged(index, revoked)} nằm vĩnh viễn trên chuỗi. Thu hồi rồi bỏ thu hồi để
   * lại đúng hai sự kiện, ai đọc log cũng thấy. Đây là tính chất mong muốn, không phải rò rỉ.
   *
   * <p>Contract <b>không ghi nếu trạng thái không đổi</b>, nên gọi lại trên chỉ số đã thu hồi
   * vẫn thành công nhưng tốn rất ít gas và không sinh sự kiện.
   *
   * @throws IllegalStateException nếu giao dịch bị revert — thường là khóa thiếu
   *     {@code STATUS_ROLE}
   */
  public TransactionReceipt setRevoked(long index, boolean revoked)
      throws IOException, TransactionException {

    if (index < 0) {
      throw new IllegalArgumentException("status_list_index không được âm: " + index);
    }

    Function fn = new Function(
        "setRevoked",
        List.of(new Uint256(BigInteger.valueOf(index)), new Bool(revoked)),
        List.of());

    BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice().multiply(GAS_PRICE_MULTIPLIER);

    EthSendTransaction sent = txManager.sendTransaction(
        gasPrice, GAS_LIMIT, contractAddress, FunctionEncoder.encode(fn), BigInteger.ZERO);

    if (sent.hasError()) {
      throw new IllegalStateException(
          "Gửi giao dịch thu hồi thất bại: " + sent.getError().getMessage());
    }

    TransactionReceipt receipt =
        receiptProcessor.waitForTransactionReceipt(sent.getTransactionHash());

    if (!receipt.isStatusOK()) {
      throw new IllegalStateException(
          "Giao dịch thu hồi bị revert: " + receipt.getTransactionHash()
              + " — khóa " + credentials.getAddress() + " có STATUS_ROLE trên StatusList không?");
    }
    return receipt;
  }
}
