package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.config.OpenApiConfig
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceChangeDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceChangePage
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceContactStatusRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceHistoryItem
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceHistoryPage
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceMemoRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsencePhaseResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceReturnRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceReturnResponse
import com.hanmaum.dn.app.features.attendance.domain.AbsenceChangeKind
import com.hanmaum.dn.app.features.attendance.domain.AbsenceChangeOrigin
import com.hanmaum.dn.app.features.attendance.domain.AbsencePhaseEndReason
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.springdoc.core.customizers.GlobalOpenApiCustomizer
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
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
                    converters.readAll(LongAbsenceResponse::class.java) +
                        converters.readAll(LongAbsenceMemoRequest::class.java) +
                        converters.readAll(LongAbsenceContactStatusRequest::class.java) +
                        converters.readAll(LongAbsenceReturnRequest::class.java) +
                        converters.readAll(LongAbsenceReturnResponse::class.java) +
                        converters.readAll(LongAbsenceHistoryPage::class.java) +
                        converters.readAll(LongAbsencePhaseResponse::class.java) +
                        converters.readAll(LongAbsenceChangePage::class.java),
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
                phasePublicId = null,
                fullName = "홍길동",
                discriminator = null,
                group = null,
                lastSundayDate = LocalDate.of(2026, 7, 26),
                absenceAnchorDate = LocalDate.of(2026, 7, 26),
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
        for (field in listOf("phasePublicId", "discriminator", "postAssignmentAttendance", "contactedBy", "contactedAt", "memo")) {
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

    /** Every serialized key is required, and every key serialized as null permits null. */
    private fun assertMatches(
        value: Any,
        schemaName: String,
    ) {
        val wire = mapper.readTree(mapper.writeValueAsString(value))
        val schema = customizedSpec().components.schemas[schemaName]!!
        assertEquals(wire.propertyNames().asSequence().toSet(), schema.required.toSet(), "$schemaName: every serialized field is required")
        wire.properties().filter { it.value.isNull }.forEach { (field, _) ->
            val property = schema.properties[field]!!
            val nullable = "null" in property.types.orEmpty() || property.anyOf.orEmpty().any { "null" in it.types.orEmpty() }
            assertTrue(nullable, "$schemaName.$field must permit null")
        }
    }

    @Test
    fun `history, phase, change and return payloads match their schemas, nulls included`() {
        val id = UUID.randomUUID()
        val day = LocalDate.of(2026, 7, 26)
        val item =
            LongAbsenceHistoryItem(
                id,
                id,
                "홍길동",
                null,
                day,
                day,
                day.plusDays(1),
                day.plusMonths(4),
                AbsencePhaseEndReason.CHECK_IN,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
            )
        assertMatches(LongAbsenceHistoryPage(listOf(item), 0, 20, 1, 1), "LongAbsenceHistoryPage")
        assertMatches(item, "LongAbsenceHistoryItem")
        val phase =
            LongAbsencePhaseResponse(
                id,
                id,
                "홍길동",
                null,
                day,
                day,
                day.plusDays(1),
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                isCurrent = true,
            )
        assertMatches(phase, "LongAbsencePhaseResponse")
        assertTrue(mapper.readTree(mapper.writeValueAsString(phase)).has("isCurrent"), "the flag keeps its is-prefix on the wire")
        val change =
            LongAbsenceChangeDto(id, 0, AbsenceChangeKind.MEMO_CHANGED, AbsenceChangeOrigin.LEGACY_SNAPSHOT, null, null, null, null, "x")
        assertMatches(change, "LongAbsenceChangeDto")
        assertMatches(LongAbsenceChangePage(listOf(change), 0, 20, 1, 1), "LongAbsenceChangePage")
        assertMatches(
            LongAbsenceReturnResponse(id, id, day, AbsencePhaseEndReason.MANUAL_RETURN, Instant.EPOCH, "kc", null, 3),
            "LongAbsenceReturnResponse",
        )
        val request = customizedSpec().components.schemas["LongAbsenceReturnRequest"]!!
        assertEquals(setOf("lastSundayDate", "version"), request.required.toSet())
    }

    @Test
    fun `the memo key is required but may be null`() {
        val schema = customizedSpec().components.schemas["LongAbsenceMemoRequest"]!!

        assertTrue("memo" in schema.required)
        assertEquals(setOf("string", "null"), schema.properties["memo"]!!.types)
        assertEquals(2000, schema.properties["memo"]!!.maxLength)
    }

    @Test
    fun `memoUpdate may be absent, null or an object`() {
        val schema = customizedSpec().components.schemas["LongAbsenceContactStatusRequest"]!!
        val variants = schema.properties["memoUpdate"]!!.anyOf.orEmpty()

        assertTrue("memoUpdate" !in schema.required.orEmpty(), "absent memoUpdate stays valid")
        assertEquals(2, variants.size)
        assertEquals("#/components/schemas/LongAbsenceMemoUpdate", variants.single { it.`$ref` != null }.`$ref`)
        assertTrue(variants.any { it.`$ref` == null && "null" in it.types.orEmpty() }, "explicit null must be permitted")
    }

    @Test
    fun `all long-absence operations document 401 and 403 and keep their own responses`() {
        fun operation(vararg codes: String) =
            Operation().responses(ApiResponses().also { r -> codes.forEach { r.addApiResponse(it, ApiResponse()) } })
        val base = "/api/v1/attendance/long-absences"
        val spec =
            OpenAPI().paths(
                Paths()
                    .addPathItem(base, PathItem().get(operation("200")))
                    .addPathItem("$base/summary", PathItem().get(operation("200")))
                    .addPathItem("$base/{memberPublicId}/contact-status", PathItem().put(operation("200", "400", "404", "409")))
                    .addPathItem("$base/{memberPublicId}/memo", PathItem().put(operation("200", "400", "404", "409")))
                    .addPathItem("$base/{memberPublicId}/return", PathItem().put(operation("200", "400", "404", "409")))
                    .addPathItem("$base/history", PathItem().get(operation("200", "400", "404")))
                    .addPathItem("$base/{memberPublicId}/phases/{phasePublicId}", PathItem().get(operation("200", "400", "404")))
                    .addPathItem("$base/{memberPublicId}/phases/{phasePublicId}/changes", PathItem().get(operation("200", "400", "404")))
                    .addPathItem("/api/v1/members", PathItem().get(operation("200"))),
            )
        OpenApiConfig.applyLongAbsenceSecurityResponses(spec, "/api/v1")

        val long =
            spec.paths
                .filterKeys { it.startsWith(base) }
                .values
                .flatMap { it.readOperations() }
        assertEquals(8, long.size)
        for (op in long) {
            assertTrue(op.responses.keys.containsAll(listOf("200", "401", "403")), "got ${op.responses.keys}")
            assertEquals(
                "#/components/schemas/ProblemDetail",
                op.responses["403"]!!
                    .content["application/problem+json"]!!
                    .schema.`$ref`,
            )
        }
        assertEquals(
            setOf("200"),
            spec.paths["/api/v1/members"]!!
                .get.responses.keys,
            "other routes are untouched",
        )
    }
}
