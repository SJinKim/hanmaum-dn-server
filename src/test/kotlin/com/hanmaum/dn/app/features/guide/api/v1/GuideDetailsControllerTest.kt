package com.hanmaum.dn.app.features.guide.api.v1

import com.hanmaum.dn.app.common.config.ApiPrefixConfig
import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsCreateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.WorshipCountResponse
import com.hanmaum.dn.app.features.guide.guideYouthExample
import com.hanmaum.dn.app.features.guide.service.GuideDetailValidationException
import com.hanmaum.dn.app.features.guide.service.GuideDetailsCalculator
import com.hanmaum.dn.app.features.guide.service.GuideDetailsService
import com.hanmaum.dn.app.features.guide.service.GuideException
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

@WebMvcTest(GuideDetailsController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class, ApiPrefixConfig::class, GuideExceptionHandler::class, GuideDetailValidationHandler::class)
class GuideDetailsControllerTest {
    @Autowired private lateinit var mvc: MockMvc

    @MockitoBean private lateinit var service: GuideDetailsService

    @MockitoBean private lateinit var members: MemberRepository

    @MockitoBean private lateinit var decoder: JwtDecoder
    private val id = UUID.randomUUID()
    private val url = "/api/v1/guide/worship-counts"
    private val calculated = GuideDetailsCalculator().calculate(guideYouthExample())
    private val response =
        GuideDetailsResponse(
            WorshipCountResponse(id, LocalDate.of(2026, 10, 25), 0, 0, 70, 70, null, null, 0, "DETAILED", true),
            calculated.entries,
            calculated.rooms,
            calculated.services,
            calculated.totals,
        )
    private val create = """{"serviceDate":"2026-10-25","entries":[]}"""
    private val update = """{"serviceDate":"2026-10-25","notes":null,"version":0,"entries":[]}"""

    private fun actor(role: String) = jwt().jwt { it.subject("guide-editor") }.authorities(SimpleGrantedAuthority("ROLE_$role"))

    @Test fun `invalid report uuid uses the documented field related validation envelope`() {
        mvc
            .perform(get("$url/not-a-uuid/details").with(actor("GUIDE_VIEWER")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data.fieldErrors.publicId").value("Invalid parameter format."))
    }

    @Test fun `viewer editor admin pastor can read while ordinary member and anonymous cannot`() {
        whenever(service.get(id)).thenReturn(response)
        for (role in listOf("GUIDE_VIEWER", "GUIDE_EDITOR", "ADMIN", "PASTOR")) {
            mvc
                .perform(get("$url/$id/details").with(actor(role)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.report.reportMode").value("DETAILED"))
                .andExpect(jsonPath("$.data.totals.totalCount").value(70))
        }
        mvc.perform(get("$url/$id/details").with(actor("MEMBER"))).andExpect(status().isForbidden)
        mvc.perform(get("$url/$id/details")).andExpect(status().isUnauthorized)
    }

    @Test fun `editor writes viewer is read only and ordinary users cannot create drafts`() {
        whenever(service.create(any(), any())).thenReturn(response)
        whenever(service.update(any(), any(), any())).thenReturn(response)
        mvc
            .perform(
                post("$url/details").with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(create),
            ).andExpect(status().isCreated)
        mvc
            .perform(
                put("$url/$id/details").with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(update),
            ).andExpect(status().isOk)
        for (role in listOf("GUIDE_VIEWER", "MEMBER")) {
            mvc
                .perform(
                    post("$url/details").with(actor(role)).contentType(MediaType.APPLICATION_JSON).content(create),
                ).andExpect(status().isForbidden)
            mvc
                .perform(
                    put("$url/$id/details").with(actor(role)).contentType(MediaType.APPLICATION_JSON).content(update),
                ).andExpect(status().isForbidden)
        }
        mvc.perform(post("$url/details").contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isUnauthorized)
    }

    @Test fun `every count field rejects coercion fractions exponent tokens and overflow with nested field errors`() {
        val invalid = listOf("-1", "1.5", "1.0", "1e3", "2147483648", "\"12\"", "\"abc\"", "true", "[]", "{}")
        for (field in listOf("adultCount", "schoolCount", "youthCount")) {
            for (value in invalid) {
                val body = """{"serviceDate":"2026-10-25","entries":[{"roomCode":"MAIN_1F_A","serviceNumber":3,"$field":$value}]}"""
                mvc
                    .perform(post("$url/details").with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.fieldErrors['entries[0].$field']").exists())
                val replacement = body.replace("\"entries\":", "\"notes\":null,\"version\":0,\"entries\":")
                val change = put("$url/$id/details").with(actor("GUIDE_EDITOR"))
                mvc
                    .perform(change.contentType(MediaType.APPLICATION_JSON).content(replacement))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.data.fieldErrors['entries[0].$field']").exists())
            }
        }
    }

    @Test fun `integer bounds zero and null survive json deserialization without coercion`() {
        whenever(service.create(any(), any())).thenReturn(response)
        for (value in listOf("0", "2147483647", "null")) {
            val body = """{"serviceDate":"2026-10-25","entries":[{"roomCode":"MAIN_1F_A","serviceNumber":3,"adultCount":$value}]}"""
            mvc
                .perform(post("$url/details").with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated)
        }
        val captured = argumentCaptor<GuideDetailsCreateRequest>()
        verify(service, times(3)).create(captured.capture(), eq("guide-editor"))
        assertEquals(listOf(0, Int.MAX_VALUE, null), captured.allValues.map { it.entries.single().adultCount })
    }

    @Test fun `derived totals mode writes and read response shaped entries are rejected rather than ignored`() {
        val cases =
            listOf(
                create.replace("\"entries\":[]", "\"entries\":[],\"totalCount\":999"),
                create.replace("\"entries\":[]", "\"entries\":[],\"reportMode\":\"SUMMARY_ONLY\""),
                create.replace("[]", """[{"roomCode":"MAIN_1F_A","serviceNumber":3,"counts":{"adultCount":40}}]"""),
            )
        cases.forEach { body ->
            mvc
                .perform(post("$url/details").with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.data.fieldErrors").isNotEmpty())
        }
    }

    @Test fun `missing version notes and invalid version or null rows return safe field errors`() {
        val cases =
            listOf(
                update.replace("\"version\":0,", "") to "version",
                update.replace("\"notes\":null,", "") to "notes",
                update.replace("\"version\":0", "\"version\":0.5") to "version",
                update.replace("\"version\":0", "\"version\":\"0\"") to "version",
                update.replace("\"version\":0", "\"version\":9223372036854775808") to "version",
                update.replace("\"version\":0", "\"version\":-1") to "version",
                update.replace("[]", "[null]") to "entries[0].<list element>",
            )
        for ((body, _) in cases) {
            mvc
                .perform(put("$url/$id/details").with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.data.fieldErrors").isNotEmpty())
        }
    }

    @Test fun `summary comparison requires all three explicit nullable integer fields`() {
        val invalid =
            listOf(
                """{"service1Count":0,"service2Count":0}""",
                """{"service1Count":"0","service2Count":0,"service3Count":0}""",
                """{"service1Count":0.5,"service2Count":0,"service3Count":0}""",
                """{"service1Count":2147483648,"service2Count":0,"service3Count":0}""",
            )
        invalid.forEach { comparison ->
            val body = update.dropLast(1) + ",\"expectedSummary\":" + comparison + "}"
            mvc
                .perform(put("$url/$id/details").with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.data.fieldErrors").isNotEmpty())
        }
        whenever(service.update(any(), any(), any())).thenReturn(response)
        val nullable = update.dropLast(1) + """, "expectedSummary":{"service1Count":null,"service2Count":null,"service3Count":null}}"""
        mvc
            .perform(put("$url/$id/details").with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(nullable))
            .andExpect(status().isOk)
    }

    @Test fun `service validation and stale mode errors preserve distinct 400 and 409 responses`() {
        whenever(
            service.create(any(), any()),
        ).thenThrow(GuideDetailValidationException(mapOf("entries[0].roomCode" to "Unknown room code.")))
        mvc
            .perform(post("$url/details").with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(create))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.data.fieldErrors['entries[0].roomCode']").exists())
        whenever(
            service.update(any(), any(), any()),
        ).thenThrow(GuideException(HttpStatus.CONFLICT, "Report changed; reload before saving."))
        mvc
            .perform(put("$url/$id/details").with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(update))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.detail").value("Report changed; reload before saving."))
    }
}
