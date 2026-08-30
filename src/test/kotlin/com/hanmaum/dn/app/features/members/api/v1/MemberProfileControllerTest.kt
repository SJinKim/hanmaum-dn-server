package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberResponse
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberService
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import kotlin.test.Test

/**
 * GET /members/me over the wire.
 *
 * MemberResponse is @Redacted, which rewrites toString(). This pins that it does *not*
 * touch JSON: a redacted field has to keep serializing, or the profile would render the
 * mask instead of the value.
 */
@WebMvcTest(MemberController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MemberProfileControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var memberService: MemberService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val registrationDate = LocalDate.of(2024, 3, 17)

    private fun profile() =
        MemberResponse(
            publicId = "pub-1",
            firstName = "철수",
            lastName = "김",
            email = "chulsoo@example.com",
            status = MemberStatus.ACTIVE,
            churchRole = "청년부원",
            registrationDate = registrationDate,
            groupName = "다니엘조",
            division = "2교구",
            birthDate = LocalDate.of(1992, 12, 7),
        )

    private fun memberToken() =
        jwt()
            .jwt { it.subject("kc-001").claim("email", "chulsoo@example.com") }
            .authorities(SimpleGrantedAuthority("ROLE_MEMBER"))

    @Test
    fun `GET members-me returns the registration date as an ISO date`() {
        `when`(memberService.getMemberProfile(eq("kc-001"), any(), any())).thenReturn(profile())

        mockMvc
            .perform(get("/api/v1/members/me").with(memberToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.registrationDate").value("2024-03-17"))
            // A redacted date field must still serialize; birthDate is the existing proof.
            .andExpect(jsonPath("$.data.birthDate").value("1992-12-07"))
            .andExpect(jsonPath("$.data.publicId").value("pub-1"))
    }

    @Test
    fun `GET members-me omits the date when the member has no registration date`() {
        `when`(memberService.getMemberProfile(eq("kc-001"), any(), any()))
            .thenReturn(
                MemberResponse(
                    publicId = "pub-1",
                    firstName = "철수",
                    lastName = "김",
                    status = MemberStatus.ACTIVE,
                    registrationDate = null,
                ),
            )

        mockMvc
            .perform(get("/api/v1/members/me").with(memberToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.registrationDate").doesNotExist())
    }

    @Test
    fun `GET members-me returns 401 without a token`() {
        mockMvc.perform(get("/api/v1/members/me")).andExpect(status().isUnauthorized)
    }
}
