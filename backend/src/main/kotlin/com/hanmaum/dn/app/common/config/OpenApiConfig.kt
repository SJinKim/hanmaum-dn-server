package com.hanmaum.dn.app.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean

@Configuration
class OpenApiConfig {

    @Bean
    fun churchOpenApi(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Church D+N API")
                    .description("API für Mitgliederverwaltung, Gruppen und Anwesenheit.")
                    .version("v1.0.0")
            )
    }
}