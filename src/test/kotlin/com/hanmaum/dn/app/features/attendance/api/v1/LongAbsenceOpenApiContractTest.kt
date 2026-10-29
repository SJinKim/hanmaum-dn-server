package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.config.OpenApiConfig
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceMemoRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceResponse
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import org.springdoc.core.customizers.GlobalOpenApiCustomizer
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The JSON of a row without follow-up must be valid against the generated contract (HDN-375). */
class LongAbsenceOpenApiContractTest {
    private val mapper = JsonMapper.builder().findAndAddModules().build()

    private fun customizedSpec(): OpenAPI {
        val converters = ModelConverters.getInstance(true)
        val spec =
            OpenAPI().components(
                Components().schemas(
                    converters.readAll(LongAbsenceResponse::class.java) + converters.readAll(LongAbsenceMemoRequest::class.java),
                ),
            )
        AnnotationConfigApplicationContext(OpenApiConfig::class.java).use { context ->
            context.getBeansOfType(GlobalOpenApiCustomizer::class.java).values.forEach { it.customise(spec) }
        }
        return spec
    }

    @Test
    fun `a row without group, profile or follow-up serializes nulls the schema permits`() {
        val row =
            LongAbsenceResponse(
                memberPublicId = UUID.randomUUID(),
                fullName = "홍길동",
                discriminator = null,
                group = null,
                lastSundayDate = LocalDate.of(2026, 7, 26),
                absentSince = LocalDate.of(2026, 7, 27),
                absenceDays = 92,
                absenceMonths = 3,
                postAssignmentAttendance = null,
                contacted = false,
                contactedBy = null,
                contactedByMember = null,
                contactedAt = null,
                memo = null,
                version = 0,
            )
        val wire = mapper.readTree(mapper.writeValueAsString(row))
        val schema = customizedSpec().components.schemas["LongAbsenceResponse"]!!
        val properties = schema.properties

        assertEquals(wire.propertyNames().asSequence().toSet(), schema.required.toSet(), "every serialized field is required")
        for (field in listOf("discriminator", "postAssignmentAttendance", "contactedBy", "contactedAt", "memo")) {
            assertTrue(wire[field].isNull, "$field is serialized as null")
            assertTrue("null" in properties[field]!!.types.orEmpty(), "$field must permit null")
        }
        assertTrue(null in properties["postAssignmentAttendance"]!!.enum, "the enum must list null")
        for (field in listOf("group", "contactedByMember")) {
            assertTrue(wire[field].isNull, "$field is serialized as null")
            val variants = properties[field]!!.anyOf.orEmpty()
            assertTrue(variants.any { it.`$ref` != null } && variants.any { "null" in it.types.orEmpty() }, "$field must permit null")
        }
        assertEquals(setOf("string"), properties["fullName"]!!.types)
    }

    @Test
    fun `the memo key is required but may be null`() {
        val schema = customizedSpec().components.schemas["LongAbsenceMemoRequest"]!!

        assertTrue("memo" in schema.required)
        assertEquals(setOf("string", "null"), schema.properties["memo"]!!.types)
        assertEquals(2000, schema.properties["memo"]!!.maxLength)
    }
}
