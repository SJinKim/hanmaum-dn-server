package com.hanmaum.dn.app.features.guide.api.v1

import com.hanmaum.dn.app.common.config.ApiPrefixConfig
import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.guide.api.v1.dto.WorshipCountResponse
import com.hanmaum.dn.app.features.guide.service.GuideException
import com.hanmaum.dn.app.features.guide.service.WorshipCountService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

@WebMvcTest(WorshipCountController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class, ApiPrefixConfig::class, GuideExceptionHandler::class)
class WorshipCountControllerTest {
    @Autowired private lateinit var mvc: MockMvc

    @MockitoBean private lateinit var service: WorshipCountService

    @MockitoBean private lateinit var members: MemberRepository

    @MockitoBean private lateinit var decoder: JwtDecoder
    private val id = UUID.randomUUID()
    private val response = WorshipCountResponse(id, LocalDate.of(2026, 10, 18), 186, 384, 146, 716, 63, null, 0, complete = true)

    private fun actor(role: String) = jwt().jwt { it.subject("test-guide") }.authorities(SimpleGrantedAuthority("ROLE_$role"))

    private val url = "/api/v1/guide/worship-counts"

    @Test fun `viewer editor admin and pastor can read but ordinary members cannot`() {
        whenever(service.get(id)).thenReturn(response)
        for (role in listOf(
            "GUIDE_VIEWER",
            "GUIDE_EDITOR",
            "ADMIN",
            "PASTOR",
        )) {
            mvc.perform(get("$url/$id").with(actor(role))).andExpect(status().isOk).andExpect(jsonPath("$.data.totalCount").value(716))
        }
        mvc.perform(get("$url/$id").with(actor("MEMBER"))).andExpect(status().isForbidden)
        mvc.perform(get("$url/$id")).andExpect(status().isUnauthorized)
    }

    @Test fun `viewer cannot write and valid editor count preserves unrecorded fields`() {
        whenever(service.create(any(), any())).thenReturn(response)
        val body = """{"serviceDate":"2026-10-18","service1Count":0,"notes":"note"}"""
        mvc
            .perform(
                post(url).with(actor("GUIDE_VIEWER")).contentType(MediaType.APPLICATION_JSON).content(body),
            ).andExpect(status().isForbidden)
        mvc
            .perform(
                post(url).with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(body),
            ).andExpect(status().isCreated)
        val request = argumentCaptor<com.hanmaum.dn.app.features.guide.api.v1.dto.WorshipCountRequest>()
        verify(service).create(request.capture(), eq("test-guide"))
        assertEquals(0, request.firstValue.service1Count)
        assertEquals(null, request.firstValue.service2Count)
    }

    @Test fun `all invalid count json types and overflow are rejected before service`() {
        for (value in listOf("-1", "1.5", "1e3", "2147483648", "\"12\"", "\"abc\"", "true", "[]", "{}")) {
            mvc
                .perform(
                    post(url)
                        .with(actor("GUIDE_EDITOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"serviceDate":"2026-10-18","service1Count":$value}"""),
                ).andExpect(status().isBadRequest)
        }
    }

    @Test fun `viewer cannot patch or delete and domain conflicts keep safe problem details`() {
        val body = """{"serviceDate":"2026-10-18","service1Count":0,"version":0}"""
        mvc
            .perform(patch("$url/$id").with(actor("GUIDE_VIEWER")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)
        mvc
            .perform(delete("$url/$id").param("version", "0").with(actor("GUIDE_VIEWER")))
            .andExpect(status().isForbidden)
        whenever(
            service.update(eq(id), any(), any()),
        ).thenThrow(GuideException(HttpStatus.CONFLICT, "Report changed; reload before saving."))
        mvc
            .perform(patch("$url/$id").with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.detail").value("Report changed; reload before saving."))
    }
}
