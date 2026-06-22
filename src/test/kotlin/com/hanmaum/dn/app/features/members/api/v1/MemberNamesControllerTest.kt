package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberNameDto
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberService
import org.mockito.Mockito.`when`
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
import java.util.UUID
import kotlin.test.Test

/**
 * Role enforcement for GET /api/v1/members/names (the ministry "회원 추가" picker).
 * Imports SecurityConfig so @PreAuthorize is actually evaluated.
 */
@WebMvcTest(MemberController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MemberNamesControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var memberService: MemberService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private fun sample() =
        listOf(
            MemberNameDto(publicId = UUID.randomUUID().toString(), fullName = "김철수", discriminator = "A"),
            MemberNameDto(publicId = UUID.randomUUID().toString(), fullName = "이영희", discriminator = null),
        )

    @Test
    fun `GET names returns 200 for admin`() {
        `when`(memberService.getMemberNames()).thenReturn(sample())

        mockMvc
            .perform(get("/api/v1/members/names").with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].fullName").value("김철수"))
            .andExpect(jsonPath("$.data[0].discriminator").value("A"))
    }

    @Test
    fun `GET names returns 200 for ministry leader`() {
        `when`(memberService.getMemberNames()).thenReturn(sample())

        mockMvc
            .perform(get("/api/v1/members/names").with(jwt().authorities(SimpleGrantedAuthority("ROLE_MINISTRY_LEADER"))))
            .andExpect(status().isOk)
    }

    @Test
    fun `GET names returns 403 for plain member`() {
        mockMvc
            .perform(get("/api/v1/members/names").with(jwt()))
            .andExpect(status().isForbidden)
    }
}
