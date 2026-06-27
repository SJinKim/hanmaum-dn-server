package com.hanmaum.dn.app.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.StringSchema
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig(
    @Value("\${api.prefix:/api/v1}") private val apiPrefix: String,
) {
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
            .pathsToMatch("$apiPrefix/**")
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

    // springdoc drops nullable UUID fields (UUID?) in Kotlin when no @field:Not* annotations
    // are present on the class. Patch the affected schemas explicitly.
    @Bean
    fun nullableUuidSchemaPatcher(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            val uuidSchema = StringSchema().format("uuid").nullable(true)
            openApi.components?.schemas?.let { schemas ->
                schemas["UpdateEventRsvpRequest"]?.addProperty("announcementId", uuidSchema)
                schemas["ActiveEventRsvpDto"]?.addProperty("announcementId", uuidSchema)
            }
        }
}
