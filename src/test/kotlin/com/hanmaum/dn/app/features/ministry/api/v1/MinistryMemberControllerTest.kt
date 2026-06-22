package com.hanmaum.dn.app.features.ministry.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.api.v1.dto.ActiveMinistryMemberDto
import com.hanmaum.dn.app.features.ministry.service.MinistryService
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.Test

/**
 * Role enforcement for POST /api/v1/ministries/{publicId}/members (the "맴버 추가" action).
 * Imports SecurityConfig so @PreAuthorize is actually evaluated (cf. EventRsvpControllerTest).
 */
@WebMvcTest(MinistryController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MinistryMemberControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var ministryService: MinistryService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val ministryId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()

    private fun sampleDto() =
        ActiveMinistryMemberDto(
            publicId = memberId.toString(),
            fullName = "김철수",
            startDate = "2026-06-01",
            note = null,
            gender = null,
        )

    private val body = """{"memberId":"$memberId"}"""

    @Test
    fun `POST add member returns 201 for admin`() {
        `when`(ministryService.addMember(eq(ministryId), any())).thenReturn(sampleDto())

        mockMvc
            .perform(
                post("/api/v1/ministries/$ministryId/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.publicId").value(memberId.toString()))
    }

    @Test
    fun `POST add member returns 201 for ministry leader`() {
        `when`(ministryService.addMember(eq(ministryId), any())).thenReturn(sampleDto())

        mockMvc
            .perform(
                post("/api/v1/ministries/$ministryId/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_MINISTRY_LEADER"))),
            ).andExpect(status().isCreated)
    }

    @Test
    fun `POST add member returns 403 for plain member`() {
        mockMvc
            .perform(
                post("/api/v1/ministries/$ministryId/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(jwt()),
            ).andExpect(status().isForbidden)
    }

    // ─── Helper: Mockito any()/eq() for non-nullable Kotlin params ────────────
    private fun <T> any(): T = org.mockito.kotlin.any()

    private fun <T> eq(value: T): T = org.mockito.kotlin.eq(value)
}
