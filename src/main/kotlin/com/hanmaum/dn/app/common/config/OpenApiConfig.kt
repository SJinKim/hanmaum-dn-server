package com.hanmaum.dn.app.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.GlobalOpenApiCustomizer
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod

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

    /**
     * Bearer auth for Swagger UI's "Authorize" and for the contract (#239). Every operation needs
     * an access token except the ones in [PublicApiRoutes], which get an empty requirement so the
     * spec does not claim a login they do not need. Global, so the v1/v2 groups get it too.
     *
     * Only a pasted access token: no OAuth flow is declared, so Swagger UI never asks for a
     * password or client secret, and `persistAuthorization` stays off, so it keeps no token.
     */
    @Bean
    fun bearerSecurityCustomizer(): GlobalOpenApiCustomizer = GlobalOpenApiCustomizer { applyBearerSecurity(it, apiPrefix) }

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

    companion object {
        const val BEARER_SCHEME = "bearerAuth"

        internal fun applyBearerSecurity(
            openApi: OpenAPI,
            apiPrefix: String,
        ) {
            val components = openApi.components ?: Components().also { openApi.components = it }
            components.addSecuritySchemes(
                BEARER_SCHEME,
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Keycloak access token of the same environment (aud hanmaum-dn-api)."),
            )
            openApi.security = listOf(SecurityRequirement().addList(BEARER_SCHEME))

            openApi.paths?.forEach { (path, item) ->
                item.readOperationsMap().forEach { (method, operation) ->
                    if (PublicApiRoutes.matches(HttpMethod.valueOf(method.name), path, apiPrefix)) {
                        operation.security = emptyList()
                    }
                }
            }
        }
    }
}
