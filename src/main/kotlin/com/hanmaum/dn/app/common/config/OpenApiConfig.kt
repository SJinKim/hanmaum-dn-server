package com.hanmaum.dn.app.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun churchOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Church D+N API")
                    .description("API für Mitgliederverwaltung, Gruppen und Anwesenheit.")
                    .version("1.0.0"),
            )

    @Bean
    fun publicApiV1(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("v1")
            .pathsToMatch("/api/v1/**")
            .displayName("Version 1 (Aktuell)")
            .build()

    @Bean
    fun publicApiV2(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .group("v2")
            .pathsToMatch("/api/v2/**")
            .displayName("Version 2 (Beta)")
            .build()
}
