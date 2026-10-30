package com.hanmaum.dn.app.common.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
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

    /** Kotlin nullability is not retained by springdoc for the RSVP response fields. */
    @Bean
    fun eventResponseNullabilityCustomizer(): GlobalOpenApiCustomizer =
        GlobalOpenApiCustomizer { openApi ->
            openApi.components?.schemas?.get("EventMemberResponseDto")?.properties?.let { properties ->
                for (field in listOf("groupName", "groupDivision", "status", "respondedAt")) {
                    properties[field]?.types = linkedSetOf("string", "null")
                }
                val status = properties["status"]
                // In OpenAPI 3.1, a nullable type alone does not override the enum constraint.
                status?.enum?.let { values ->
                    if (null !in values) status.addEnumItemObject(null)
                }
            }
        }

    /** A purpose without a standing grant (MISSING) serializes both granted fields as null. */
    @Bean
    fun consentStatusNullabilityCustomizer(): GlobalOpenApiCustomizer =
        GlobalOpenApiCustomizer { openApi ->
            openApi.components?.schemas?.get("MemberConsentStatus")?.properties?.let { properties ->
                for (field in listOf("grantedVersion", "grantedAt")) {
                    properties[field]?.let { it.types = linkedSetOf("string", "null") }
                }
            }
        }

    /**
     * 장기결석자 (HDN-375): every response field is serialized, nulls included, and springdoc keeps
     * no Kotlin nullability. Marks all fields required and the nullable ones as such, including
     * null in the enum and the nullable object references.
     */
    @Bean
    fun longAbsenceNullabilityCustomizer(): GlobalOpenApiCustomizer = GlobalOpenApiCustomizer { applyLongAbsenceNullability(it) }

    /**
     * 401/403 of the pastoral-care class guard on all four long-absence operations. Added here, not
     * with a class-level annotation: springdoc drops the default 200 of an operation that declares
     * any response, and the two GETs declare none of their own.
     */
    @Bean
    fun longAbsenceSecurityResponsesCustomizer(): GlobalOpenApiCustomizer =
        GlobalOpenApiCustomizer { applyLongAbsenceSecurityResponses(it, apiPrefix) }

    companion object {
        const val BEARER_SCHEME = "bearerAuth"

        internal fun applyLongAbsenceNullability(openApi: OpenAPI) {
            val schemas = openApi.components?.schemas ?: return
            schemas["LongAbsenceResponse"]?.let { response ->
                val properties = response.properties ?: return@let
                for (field in listOf("discriminator", "postAssignmentAttendance", "contactedBy", "contactedAt", "memo")) {
                    properties[field]?.types = linkedSetOf("string", "null")
                }
                properties["postAssignmentAttendance"]?.let { attendance ->
                    attendance.enum?.let { values -> if (null !in values) attendance.addEnumItemObject(null) }
                }
                for (field in listOf("group", "contactedByMember")) {
                    val reference = properties[field]?.`$ref` ?: continue
                    response.addProperty(
                        field,
                        Schema<Any>()
                            .anyOf(listOf(Schema<Any>().`$ref`(reference), Schema<Any>().types(setOf("null"))))
                            .description(properties[field]?.description),
                    )
                }
                response.required = response.properties.keys.toList()
            }
            // memoUpdate absent or null leaves the memo unchanged; {value: null} stays the clearing operation.
            schemas["LongAbsenceContactStatusRequest"]?.let { request ->
                val reference = request.properties?.get("memoUpdate")?.`$ref` ?: return@let
                request.addProperty(
                    "memoUpdate",
                    Schema<Any>()
                        .anyOf(listOf(Schema<Any>().`$ref`(reference), Schema<Any>().types(setOf("null"))))
                        .description(request.properties["memoUpdate"]?.description),
                )
            }
            schemas["LongAbsenceGroupDto"]?.let { group ->
                group.properties?.get("division")?.types = linkedSetOf("string", "null")
                group.required = group.properties?.keys?.toList()
            }
            for (name in listOf("LongAbsenceActorDto", "LongAbsenceSummaryResponse")) {
                schemas[name]?.let { it.required = it.properties?.keys?.toList() }
            }
        }

        internal fun applyLongAbsenceSecurityResponses(
            openApi: OpenAPI,
            apiPrefix: String,
        ) {
            val problem =
                Content().addMediaType(
                    "application/problem+json",
                    MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/ProblemDetail")),
                )
            openApi.paths?.filterKeys { it.startsWith("$apiPrefix/attendance/long-absences") }?.values?.forEach { item ->
                item.readOperations().forEach { operation ->
                    operation.responses?.apply {
                        addApiResponse("401", ApiResponse().description("Missing or invalid access token").content(problem))
                        addApiResponse("403", ApiResponse().description("Caller is not PASTOR or ADMIN").content(problem))
                    }
                }
            }
        }

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
