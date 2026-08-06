package vn.ptit.drl.credential;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;

import lombok.extern.slf4j.Slf4j;
import vn.ptit.drl.common.config.DrlProperties;

/**
 * Dựng {@link IssuerSigner} — <b>chỉ khi có khóa</b>.
 *
 * <p>Cùng lý do với {@code AnchorChainConfig}: phần lớn thời gian phát triển và toàn bộ test
 * không cần ký credential, và bắt buộc có khóa nghĩa là ứng dụng không khởi động được khi
 * {@code .env} chưa điền. Nơi nào cần thì tiêm bằng {@code ObjectProvider}.
 *
 * <p>Khác {@code AnchorChainConfig} ở chỗ <b>không</b> cần {@code drl.anchor.enabled}: ký
 * credential là phép tính cục bộ, không chạm RPC. Cấp credential được ngay cả khi chuỗi đang
 * tắt — chỉ có phần <i>neo</i> nó là phải đợi job.
 */
@Configuration
@ConditionalOnExpression("!'${drl.credential.issuer-private-key:}'.isBlank()")
@Slf4j
public class CredentialConfig {

  @Bean
  public IssuerSigner issuerSigner(DrlProperties props) {
    IssuerSigner signer = new IssuerSigner(props.credential().issuerPrivateKey());

    IssuerSigner.warnIfSameAsAnchorKey(
        props.credential().issuerPrivateKey(), props.anchor().privateKey(), log);

    log.info("Khoa cap credential: {}", signer.address());
    return signer;
  }

  /**
   * {@link StatusListClient} — <b>chỉ khi chuỗi đang bật</b>.
   *
   * <p>Tiêm {@code ObjectProvider<Web3j>} chứ không tiêm thẳng {@link Web3j}: bean đó do
   * {@code AnchorChainConfig} dựng và chỉ tồn tại khi {@code drl.anchor.enabled=true}. Tiêm
   * thẳng sẽ làm ứng dụng không khởi động được ở cấu hình mặc định — trong khi <b>cấp</b>
   * credential vẫn chạy tốt không cần chuỗi, chỉ <b>thu hồi</b> mới cần.
   *
   * <p>Ký bằng {@code ANCHOR_PRIVATE_KEY}, không phải khóa issuer: quyền cần ở đây là
   * {@code STATUS_ROLE} trên contract, và đó là vai trò vận hành hệ thống — cùng họ với
   * {@code ANCHOR_ROLE} — chứ không phải vai trò "tổ chức phát biểu điều gì đó" của khóa
   * issuer. Thu hồi là hành động của <b>người vận hành sổ trạng thái</b>.
   */
  @Bean
  public StatusListClient statusListClient(ObjectProvider<Web3j> web3jProvider,
                                           DrlProperties props) {
    Web3j web3j = web3jProvider.getIfAvailable();
    if (web3j == null) {
      log.info("Chuoi dang tat — khong dung StatusListClient. Thu hoi credential se bao loi"
          + " ro rang thay vi ghi CSDL mot minh.");
      return null;
    }

    var anchor = props.anchor();
    if (anchor.statusListAddress() == null || anchor.statusListAddress().isBlank()) {
      log.warn("Thieu STATUS_LIST_ADDRESS trong .env — khong thu hoi credential duoc.");
      return null;
    }

    var client = new StatusListClient(
        web3j, anchor.statusListAddress(),
        Credentials.create(anchor.privateKey()), anchor.chainId());

    log.info("StatusList {} · vi ky {}", anchor.statusListAddress(), client.signerAddress());
    return client;
  }
}
