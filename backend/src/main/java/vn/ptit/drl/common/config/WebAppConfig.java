package vn.ptit.drl.common.config;

import java.io.IOException;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Phục vụ PWA đã build từ chính backend — <b>một origin duy nhất cho cả app lẫn API</b>.
 *
 * <h2>Vì sao không tách hai origin</h2>
 *
 * <p>Điện thoại Android chỉ <b>cài được PWA khi trang chạy trên HTTPS</b>. Nếu app nằm ở một
 * origin HTTPS còn API ở {@code http://192.168.x.x:8080}, trình duyệt <b>chặn thẳng</b> mọi
 * lời gọi API vì mixed content — app cài được nhưng đăng nhập không nổi.
 *
 * <p>Gộp một origin xóa cả hai vấn đề cùng lúc: không mixed content, và không CORS.
 * {@code app/src/lib/auth.ts} vốn đã đặt {@code OpenAPI.BASE = ''} (cùng origin), nên phía
 * client không phải đổi một dòng nào.
 *
 * <p>Đổi lại: mỗi lần sửa giao diện phải build lại PWA và chép vào
 * {@code backend/src/main/resources/static}. Có {@code scripts/build-pwa.ps1} làm việc đó.
 * Lúc phát triển vẫn dùng {@code vite} với proxy như cũ — cấu hình này chỉ có tác dụng khi
 * thư mục {@code static} tồn tại.
 *
 * <h2>Fallback cho route phía client</h2>
 *
 * <p>React Router xử lý {@code /sv/diem} trong trình duyệt, nhưng khi người dùng <b>tải lại
 * trang</b> ở đường dẫn đó thì trình duyệt hỏi thẳng máy chủ. Không có fallback thì Spring trả
 * 404 và app trắng — lỗi kinh điển của mọi SPA đặt sau máy chủ tĩnh.
 *
 * <p>Fallback <b>cố ý không đụng</b> {@code /api}, {@code /v3}, {@code /swagger-ui},
 * {@code /actuator}, {@code /error}: một đường API gõ sai phải trả <b>404 JSON</b>, chứ trả về
 * trang HTML thì client sẽ cố phân tích HTML thành JSON và báo một lỗi chẳng liên quan gì.
 */
@Configuration
public class WebAppConfig implements WebMvcConfigurer {

  /** Tiền tố KHÔNG bao giờ được rơi vào fallback SPA. */
  private static final String[] KHONG_FALLBACK = {
      "api/", "v3/", "swagger-ui", "actuator/", "error"
  };

  /**
   * Dạy Tomcat kiểu MIME cho {@code .webmanifest}.
   *
   * <p>Bảng MIME mặc định của Tomcat <b>không có</b> đuôi này, nên manifest bị trả về
   * {@code application/octet-stream}. Spring Security đồng thời gắn
   * {@code X-Content-Type-Options: nosniff} vào mọi phản hồi, nên Chrome <b>không được phép</b>
   * đoán lại kiểu và bỏ luôn manifest.
   *
   * <p>Hậu quả rất khó lần ra: trang tải bình thường, DevTools không báo lỗi đỏ, nhưng Chrome
   * trên Android coi trang là <b>không đủ điều kiện cài</b>. Trong menu, mục "Cài đặt và tạo lối
   * tắt" vẫn còn, nhưng bấm vào thì bảng chọn chỉ mời "Tạo lối tắt" — một shortcut mở trong
   * trình duyệt, không phải WebAPK. Lựa chọn "Cài đặt" biến mất.
   *
   * <p>Kiểu đúng theo chuẩn W3C Web App Manifest là {@code application/manifest+json}.
   *
   * <h2>Bẫy: phải duyệt DEFAULT, không được sao chép nó</h2>
   *
   * <p>{@code setMimeMappings()} <b>thay thế</b> toàn bộ bảng chứ không cộng thêm, nên phải tự
   * dựng lại bảng mặc định. Nhưng {@code new MimeMappings(MimeMappings.DEFAULT)} cho ra
   * <b>bảng rỗng</b>: {@code DEFAULT} nạp lười, và constructor sao chép không kích hoạt việc nạp
   * đó. Đã đo: DEFAULT có 1021 mục, bản sao có 0.
   *
   * <p>Duyệt bằng vòng lặp thì việc nạp được kích hoạt và đủ 1021 mục. Mất bảng mặc định còn
   * nguy hơn lỗi ban đầu — {@code .js} rơi về {@code octet-stream} và trình duyệt từ chối chạy
   * service worker.
   */
  @Bean
  public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> mimeChoManifest() {
    return factory -> {
      MimeMappings bang = new MimeMappings();
      for (MimeMappings.Mapping macDinh : MimeMappings.DEFAULT) {
        bang.add(macDinh.getExtension(), macDinh.getMimeType());
      }
      bang.add("webmanifest", "application/manifest+json");
      factory.setMimeMappings(bang);
    };
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(new PathResourceResolver() {
          @Override
          protected Resource getResource(String resourcePath, Resource location)
              throws IOException {

            Resource yeuCau = location.createRelative(resourcePath);
            if (yeuCau.exists() && yeuCau.isReadable()) {
              return yeuCau;
            }

            for (String tienTo : KHONG_FALLBACK) {
              if (resourcePath.startsWith(tienTo)) {
                return null;
              }
            }

            // Route của React Router — trả index.html để app tự định tuyến.
            Resource index = new ClassPathResource("/static/index.html");
            return index.exists() ? index : null;
          }
        });
  }
}
