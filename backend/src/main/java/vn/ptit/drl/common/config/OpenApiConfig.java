package vn.ptit.drl.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Spec này là nguồn sinh TypeScript client cho PWA (quyết định số 3, tiết kiệm ~3 ngày):
 * <pre>
 *   npx openapi-typescript-codegen --input http://localhost:8080/v3/api-docs \
 *                                  --output app/src/api/generated
 * </pre>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI drlOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DRL API — Sổ tay hoạt động sinh viên")
                        .version("v1")
                        .description("Điểm danh chống gian lận, chấm điểm rèn luyện, "
                                + "neo dữ liệu lên Polygon Amoy."))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
