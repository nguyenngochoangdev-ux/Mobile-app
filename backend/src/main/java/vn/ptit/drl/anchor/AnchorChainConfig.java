package vn.ptit.drl.anchor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import lombok.extern.slf4j.Slf4j;
import vn.ptit.drl.common.config.DrlProperties;

/**
 * Dựng kết nối chuỗi — <b>chỉ khi {@code drl.anchor.enabled=true}</b>.
 *
 * <p>Mặc định tắt là có chủ ý: phần lớn thời gian phát triển và toàn bộ test không cần chạm
 * tới chuỗi, và bật sẵn nghĩa là ứng dụng không khởi động được khi không có mạng hoặc khi
 * `.env` chưa điền khóa. Bật bằng `ANCHOR_ENABLED=true`.
 *
 * <p>Bean tạo ra là {@link AnchorRegistryClient}; nơi nào cần thì tiêm bằng
 * {@code ObjectProvider} để vẫn chạy được khi chuỗi đang tắt.
 */
@Configuration
@ConditionalOnProperty(name = "drl.anchor.enabled", havingValue = "true")
@Slf4j
public class AnchorChainConfig {

  @Bean(destroyMethod = "shutdown")
  public Web3j web3j(DrlProperties props) {
    String url = props.anchor().rpcUrl();
    if (url == null || url.isBlank()) {
      throw new IllegalStateException(
          "drl.anchor.enabled=true nhưng AMOY_RPC_URL rỗng. Điền vào .env,"
              + " hoặc đặt ANCHOR_ENABLED=false nếu chưa cần chuỗi.");
    }
    log.info("Ket noi RPC: {}", maskUrl(url));
    return Web3j.build(new HttpService(url));
  }

  @Bean
  public AnchorRegistryClient anchorRegistryClient(Web3j web3j, DrlProperties props) {
    var anchor = props.anchor();

    if (anchor.anchorRegistryAddress() == null || anchor.anchorRegistryAddress().isBlank()) {
      throw new IllegalStateException(
          "Thiếu ANCHOR_REGISTRY_ADDRESS. Deploy bằng `cd contracts && npm run deploy:amoy`"
              + " rồi dán địa chỉ vào .env.");
    }
    if (anchor.privateKey() == null || anchor.privateKey().isBlank()) {
      throw new IllegalStateException(
          "Thiếu ANCHOR_PRIVATE_KEY — khóa ký giao dịch neo. Khóa này phải có ANCHOR_ROLE"
              + " trên AnchorRegistry.");
    }

    var client = new AnchorRegistryClient(
        web3j, anchor.anchorRegistryAddress(),
        Credentials.create(anchor.privateKey()), anchor.chainId());

    log.info("AnchorRegistry {} · vi neo {} · chainId {}",
        anchor.anchorRegistryAddress(), client.anchorerAddress(), anchor.chainId());
    return client;
  }

  /** Giấu API key khi URL có dạng .../v2/KEY — đừng để key rơi vào log. */
  private static String maskUrl(String url) {
    int lastSlash = url.lastIndexOf('/');
    if (lastSlash > 8 && lastSlash < url.length() - 8) {
      return url.substring(0, lastSlash + 1) + "***";
    }
    return url;
  }
}
