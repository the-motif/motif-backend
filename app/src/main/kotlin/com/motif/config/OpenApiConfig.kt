package com.motif.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Swagger UI: /swagger-ui/index.html / 우측 Authorize에 액세스 토큰 입력
@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(Info().title("Motif API").version("v1"))
        .components(
            Components().addSecuritySchemes(
                BEARER,
                SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
            ),
        )
        .addSecurityItem(SecurityRequirement().addList(BEARER))

    companion object {
        private const val BEARER = "bearer"
    }
}
