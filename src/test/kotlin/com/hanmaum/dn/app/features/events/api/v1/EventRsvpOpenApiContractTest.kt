package com.hanmaum.dn.app.features.events.api.v1

import com.hanmaum.dn.app.common.config.OpenApiConfig
import com.hanmaum.dn.app.features.events.api.v1.dto.EventMemberResponseDto
import com.hanmaum.dn.app.features.events.api.v1.dto.EventResponsesDto
import com.hanmaum.dn.app.features.events.domain.RsvpStatus
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.Json31
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import org.springdoc.core.customizers.GlobalOpenApiCustomizer
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventRsvpOpenApiContractTest {
    private val mapper = JsonMapper.builder().findAndAddModules().build()

    private fun generatedSpec(): OpenAPI =
        OpenAPI().components(
            Components().schemas(ModelConverters.getInstance(true).readAll(EventResponsesDto::class.java)),
        )

    private fun customize(spec: OpenAPI) {
        AnnotationConfigApplicationContext(OpenApiConfig::class.java).use { context ->
            context.getBeansOfType(OpenApiCustomizer::class.java).values.forEach { it.customise(spec) }
        }
    }

    @Test
    fun `unanswered ungrouped member JSON is allowed by the generated schema`() {
        val row = EventMemberResponseDto(UUID.randomUUID(), "Contract fixture", null, null, null, null)
        val wire = mapper.readTree(mapper.writeValueAsString(row))
        val spec = generatedSpec().also(::customize)
        val properties =
            Json31
                .mapper()
                .valueToTree<com.fasterxml.jackson.databind.JsonNode>(spec)
                .at("/components/schemas/EventMemberResponseDto/properties")

        for (field in listOf("groupName", "groupDivision", "status", "respondedAt")) {
            assertTrue(wire[field].isNull, "$field is actually serialized as null")
            assertTrue(properties[field]["type"].any { it.asText() == "null" }, "$field must permit null")
        }
        assertTrue(properties["status"]["enum"].any { it.isNull }, "the enum must also permit JSON null")
    }

    @Test
    fun `answered member statuses and timestamp format remain in the contract`() {
        val spec = generatedSpec().also(::customize)
        val properties = spec.components.schemas["EventMemberResponseDto"]!!.properties
        val status = properties["status"]!!
        assertEquals(RsvpStatus.entries.map { it.name }.toSet() + null, status.enum.toSet())
        assertEquals(setOf("string", "null"), status.types)
        assertEquals("date-time", properties["respondedAt"]!!.format)
        assertEquals("uuid", properties["memberPublicId"]!!.format)
        assertEquals(setOf("string"), properties["memberName"]!!.types)

        for (answer in RsvpStatus.entries) {
            val row = EventMemberResponseDto(UUID.randomUUID(), "Contract fixture", "Group", "Division", answer, OffsetDateTime.now())
            val wire = mapper.readTree(mapper.writeValueAsString(row))
            assertTrue(wire["status"].asString() in status.enum)
            assertTrue(wire["respondedAt"].isString)
        }
    }

    @Test
    fun `global customizers also cover grouped docs and preserve overview fields`() {
        val spec = generatedSpec()
        val before = Json31.mapper().writeValueAsString(spec.components.schemas["EventResponsesDto"])
        AnnotationConfigApplicationContext(OpenApiConfig::class.java).use { context ->
            context.getBeansOfType(GlobalOpenApiCustomizer::class.java).values.forEach { it.customise(spec) }
        }
        assertEquals(before, Json31.mapper().writeValueAsString(spec.components.schemas["EventResponsesDto"]))
        assertTrue(
            "null" in
                spec.components.schemas["EventMemberResponseDto"]!!
                    .properties["status"]!!
                    .types
                    .orEmpty(),
        )
    }
}
