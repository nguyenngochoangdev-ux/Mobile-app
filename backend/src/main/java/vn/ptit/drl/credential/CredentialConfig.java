package vn.ptit.drl.credential;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
