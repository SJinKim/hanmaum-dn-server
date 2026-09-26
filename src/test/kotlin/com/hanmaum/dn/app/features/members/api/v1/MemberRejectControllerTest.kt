package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberService
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.Test

/** Rejecting a registration is an admin decision; nobody else may reach the endpoint. */
@WebMvcTest(MemberController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MemberRejectControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var memberService: MemberService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val publicId: UUID = UUID.randomUUID()

    @Test
    fun `anonymous cannot reject a member`() {
        mockMvc
            .perform(post("/api/v1/members/$publicId/reject"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a plain user cannot reject a member`() {
        mockMvc
            .perform(post("/api/v1/members/$publicId/reject").with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden)
        verify(memberService, never()).rejectMember(any())
    }

    @Test
    fun `an admin rejects a member through the service`() {
        mockMvc
            .perform(post("/api/v1/members/$publicId/reject").with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk)
        verify(memberService).rejectMember(publicId)
    }
}
