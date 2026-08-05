package vn.ptit.drl.anchor;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Bytes8;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.abi.datatypes.generated.Uint64;
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
 * Gọi contract {@code AnchorRegistry} trên Polygon Amoy.
 *
 * <h2>Vì sao viết tay thay vì sinh wrapper bằng web3j codegen</h2>
 *
 * <p>Contract này có đúng ba hàm cần dùng. Wrapper sinh ra dài khoảng 500 dòng, và
 * {@code .gitignore} loại nó ra khỏi repo (mã sinh không commit) — nghĩa là mỗi lần clone
 * phải chạy lại codegen trước khi biên dịch được. Với một đề tài mà rủi ro lớn nhất là hỏng
 * việc ngay trước buổi bảo vệ, một phụ thuộc build thêm để tiết kiệm 80 dòng là đánh đổi sai.
 * Không thêm plugin, không thêm dependency — chỉ dùng {@code web3j core} vốn đã có.
 *
 * <h2>Ranh giới module</h2>
 *
 * <p>Lớp này chỉ biết {@code bytes8} / {@code uint64} / {@code bytes32}. Nó không biết bản ghi
 * điểm danh hay credential là gì (PROJECT.md §5).
 *
 * <h2>Đọc không cần khóa</h2>
 *
 * <p>{@link #getRoot} và {@link #batchCount} là {@code eth_call} — chạy được trên endpoint
 * công cộng không key, đúng thứ verifier tĩnh cần. Chỉ {@link #anchor} mới cần khóa ký.
 */
public class AnchorRegistryClient {

  /** Đủ rộng cho `anchor()` (đo local: ~55.000) kèm biên an toàn. */
  private static final BigInteger GAS_LIMIT = BigInteger.valueOf(200_000);

  /** Amoy có phí ưu tiên tối thiểu; nhân thêm để tx không nằm chờ giữa buổi demo. */
  private static final BigInteger GAS_PRICE_MULTIPLIER = BigInteger.valueOf(2);

  private final Web3j web3j;
  private final String contractAddress;
  private final Credentials credentials;
  private final RawTransactionManager txManager;
  private final PollingTransactionReceiptProcessor receiptProcessor;

  public AnchorRegistryClient(Web3j web3j, String contractAddress, Credentials credentials,
                              long chainId) {
    this.web3j = web3j;
    this.contractAddress = contractAddress;
    this.credentials = credentials;
    // Chờ tối đa ~5 phút (60 lần × 5 giây). Amoy thường chốt block trong vài giây; biên rộng
    // là để buổi nghiệm thu không phụ thuộc vào lúc mạng chậm.
    this.receiptProcessor = new PollingTransactionReceiptProcessor(web3j, 5_000L, 60);
    this.txManager = new RawTransactionManager(web3j, credentials, chainId, receiptProcessor);
  }

  public String anchorerAddress() {
    return credentials.getAddress();
  }

  // ---------------------------------------------------------------- đọc

  /**
   * Đọc root đã neo. Trả về 32 byte {@code 0x00} nếu lô chưa neo — không nhập nhằng, vì
   * contract chặn ghi root rỗng.
   */
  public byte[] getRoot(AnchorDomain domain, long batchId) throws IOException {
    Function fn = new Function(
        "getRoot",
        List.of(new Bytes8(domain.toBytes8()), new Uint64(BigInteger.valueOf(batchId))),
        List.of(new TypeReference<Bytes32>() {}));

    List<Type> out = call(fn);
    return (byte[]) out.get(0).getValue();
  }

  /** Số lô đã neo của một miền. */
  public long batchCount(AnchorDomain domain) throws IOException {
    Function fn = new Function(
        "batchCount",
        List.of(new Bytes8(domain.toBytes8())),
        List.of(new TypeReference<Uint64>() {}));

    List<Type> out = call(fn);
    return ((BigInteger) out.get(0).getValue()).longValueExact();
  }

  private List<Type> call(Function fn) throws IOException {
    EthCall response = web3j.ethCall(
        Transaction.createEthCallTransaction(
            credentials.getAddress(), contractAddress, FunctionEncoder.encode(fn)),
        DefaultBlockParameterName.LATEST).send();

    if (response.hasError()) {
      throw new IOException(
          "eth_call thất bại: " + response.getError().getMessage());
    }
    List<Type> decoded =
        FunctionReturnDecoder.decode(response.getValue(), fn.getOutputParameters());
    if (decoded.isEmpty()) {
      throw new IOException(
          "eth_call trả về rỗng — sai địa chỉ contract, hay contract chưa deploy ở mạng này?");
    }
    return decoded;
  }

  // ---------------------------------------------------------------- ghi

  /**
   * Neo một Merkle root.
   *
   * <p><b>Không thể hoàn tác.</b> Contract cố ý không cho ghi đè, nên mỗi
   * {@code (domain, batchId)} chỉ dùng được đúng một lần — kể cả admin cũng không sửa được.
   * Đó là chỗ luận điểm "chống sửa hồi tố" sống hay chết, nên bên gọi phải chắc chắn về
   * {@code root} trước khi gọi.
   *
   * @throws IllegalStateException nếu giao dịch bị revert (thường là lô đã neo rồi, hoặc
   *     khóa đang dùng không có {@code ANCHOR_ROLE})
   */
  public TransactionReceipt anchor(AnchorDomain domain, long batchId, byte[] root, int leafCount)
      throws IOException, TransactionException {

    if (root == null || root.length != MerkleService.HASH_BYTES) {
      throw new IllegalArgumentException(
          "root phải dài đúng " + MerkleService.HASH_BYTES + " byte");
    }
    if (leafCount <= 0) {
      throw new IllegalArgumentException("leafCount phải dương, nhận được: " + leafCount);
    }

    Function fn = new Function(
        "anchor",
        List.of(
            new Bytes8(domain.toBytes8()),
            new Uint64(BigInteger.valueOf(batchId)),
            new Bytes32(root),
            new Uint32(BigInteger.valueOf(leafCount))),
        List.of());

    BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice().multiply(GAS_PRICE_MULTIPLIER);

    EthSendTransaction sent = txManager.sendTransaction(
        gasPrice, GAS_LIMIT, contractAddress, FunctionEncoder.encode(fn), BigInteger.ZERO);

    if (sent.hasError()) {
      throw new IllegalStateException(
          "Gửi giao dịch neo thất bại: " + sent.getError().getMessage());
    }

    TransactionReceipt receipt =
        receiptProcessor.waitForTransactionReceipt(sent.getTransactionHash());

    if (!receipt.isStatusOK()) {
      throw new IllegalStateException(
          "Giao dịch neo bị revert: " + receipt.getTransactionHash()
              + " — lô (" + domain + ", " + batchId + ") đã neo rồi,"
              + " hay khóa " + credentials.getAddress() + " thiếu ANCHOR_ROLE?");
    }
    return receipt;
  }
}
