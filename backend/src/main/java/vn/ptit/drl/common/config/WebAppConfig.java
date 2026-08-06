package vn.ptit.drl.common.config;

import java.io.IOException;

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
