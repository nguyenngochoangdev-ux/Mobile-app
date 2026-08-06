package vn.ptit.drl.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.MimeMappings;

/**
 * Canh kiểu MIME của {@code .webmanifest}.
 *
 * <p>Lỗi này đã sập một lần thật trên điện thoại Android. Bảng MIME mặc định của Tomcat không có
 * đuôi {@code .webmanifest}, nên manifest bị trả về {@code application/octet-stream}. Spring
 * Security lại gắn {@code X-Content-Type-Options: nosniff} vào mọi phản hồi, nên Chrome không
 * được phép đoán lại kiểu và bỏ luôn manifest.
 *
 * <p>Triệu chứng cực kỳ khó lần: trang tải bình thường, không có lỗi đỏ nào. Chỉ khác đúng một
 * chỗ, mà chỗ đó phải bấm vào mới thấy. Menu Chrome vẫn có mục "Cài đặt và tạo lối tắt", nhưng
 * bảng chọn hiện ra chỉ mời "Tạo lối tắt", mất lựa chọn "Cài đặt".
 *
 * <p>Test kiểm thẳng bảng MIME chứ không gọi HTTP. Tệp {@code static/manifest.webmanifest} là sản
 * phẩm của {@code scripts/build-pwa.ps1} và đã bị gitignore, nên test đọc tệp sẽ đỏ trên bản
 * checkout sạch — đỏ vì môi trường, không phải vì mã sai.
 */
class WebAppConfigMimeTest {

  /** Chạy customizer lên một factory thật rồi đọc lại bảng MIME nó để lại. */
  private MimeMappings bangMimeSauKhiCauHinh() {
    TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
    new WebAppConfig().mimeChoManifest().customize(factory);
    return factory.getMimeMappings();
  }

  @Test
  @DisplayName("webmanifest trả về application/manifest+json, không phải octet-stream")
  void webmanifestDungKieuChuan() {
    assertThat(bangMimeSauKhiCauHinh().get("webmanifest"))
        .isEqualTo("application/manifest+json");
  }

  @Test
  @DisplayName("Không đuôi mặc định nào bị mất")
  void khongLamMatBangMacDinh() {
    // Test này đã bắt được một lỗi thật. setMimeMappings() THAY THẾ toàn bộ bảng chứ
    // không cộng thêm, mà `new MimeMappings(MimeMappings.DEFAULT)` lại trả về bảng RỖNG
    // vì DEFAULT nạp lười. Bản sửa đầu tiên vì thế xóa sạch 1021 mục và chỉ giữ lại
    // webmanifest — nặng hơn lỗi ban đầu, vì .js mất kiểu MIME thì trình duyệt từ chối
    // chạy service worker.
    MimeMappings bang = bangMimeSauKhiCauHinh();

    // So từng mục thay vì so số lượng: đếm bằng nhau vẫn có thể sai nội dung.
    for (MimeMappings.Mapping macDinh : MimeMappings.DEFAULT) {
      assertThat(bang.get(macDinh.getExtension()))
          .as("đuôi .%s", macDinh.getExtension())
          .isEqualTo(macDinh.getMimeType());
    }

    // Vài đuôi PWA sống chết dựa vào, ghi thẳng ra cho người đọc thấy ngay.
    assertThat(bang.get("js")).isEqualTo("text/javascript");
    assertThat(bang.get("png")).isEqualTo("image/png");
    assertThat(bang.get("json")).isEqualTo("application/json");
    assertThat(bang.get("svg")).isEqualTo("image/svg+xml");
  }
}
