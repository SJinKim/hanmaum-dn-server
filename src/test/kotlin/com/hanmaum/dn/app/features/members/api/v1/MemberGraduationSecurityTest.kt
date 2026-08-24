package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberGraduationService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.Test

/**
 * Graduating deactivates a member and the notes may name other people, so every endpoint
 * must be closed to anonymous and to non-admin callers.
 *
 * Imports SecurityConfig so the real filter chain and @EnableMethodSecurity apply; the
 * default @WebMvcTest slice leaves both out and would pass without proving anything.
 */
@WebMvcTest(
    MemberGraduationController::class,
    excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class],
)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MemberGraduationSecurityTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var graduationService: MemberGraduationService

    // WebMvcConfig registers MemberStatusInterceptor, which the slice instantiates.
    @MockitoBean
    private lateinit var memberRepository: MemberRepository

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private val publicId: UUID = UUID.randomUUID()

    /** An authenticated caller with no admin role. */
    private fun plainUser() = jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))

    @Test
    fun `anonymous cannot graduate a member`() {
        mockMvc
            .perform(
                post("/api/v1/members/$publicId/graduation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"graduatedOn":"2026-05-01","reason":"MARRIAGE"}"""),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a plain user cannot graduate a member`() {
        mockMvc
            .perform(
                post("/api/v1/members/$publicId/graduation")
                    .with(plainUser())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"graduatedOn":"2026-05-01","reason":"MARRIAGE"}"""),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `a plain user cannot reinstate a member`() {
        mockMvc
            .perform(delete("/api/v1/members/$publicId/graduation").with(plainUser()))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `a plain user cannot read graduation state`() {
        mockMvc
            .perform(get("/api/v1/members/$publicId/graduation").with(plainUser()))
            .andExpect(status().isForbidden)
    }
}
