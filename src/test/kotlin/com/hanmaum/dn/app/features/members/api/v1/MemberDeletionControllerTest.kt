package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberPurgeService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.Test

/** Restoring and permanently deleting a deleted member are admin decisions (#246). */
@WebMvcTest(MemberController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MemberDeletionControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var memberService: MemberService

    @MockitoBean private lateinit var memberPurgeService: MemberPurgeService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val publicId: UUID = UUID.randomUUID()

    private fun role(name: String) = jwt().authorities(SimpleGrantedAuthority("ROLE_$name"))

    @Test
    fun `anonymous can neither restore nor permanently delete`() {
        mockMvc.perform(post("/api/v1/members/$publicId/restore")).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/api/v1/members/$publicId/permanent")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a note_taker can neither restore nor permanently delete`() {
        mockMvc
            .perform(post("/api/v1/members/$publicId/restore").with(role("NOTE_TAKER")))
            .andExpect(status().isForbidden)
        mockMvc
            .perform(delete("/api/v1/members/$publicId/permanent").with(role("NOTE_TAKER")))
            .andExpect(status().isForbidden)
        verify(memberService, never()).restoreMember(any())
        verify(memberPurgeService, never()).purgeMember(any())
    }

    @Test
    fun `a plain user cannot permanently delete`() {
        mockMvc
            .perform(delete("/api/v1/members/$publicId/permanent").with(role("USER")))
            .andExpect(status().isForbidden)
        verify(memberPurgeService, never()).purgeMember(any())
    }

    @Test
    fun `an admin restores a member through the service`() {
        mockMvc
            .perform(post("/api/v1/members/$publicId/restore").with(role("ADMIN")))
            .andExpect(status().isOk)
        verify(memberService).restoreMember(publicId)
    }

    @Test
    fun `an admin permanently deletes a member through the purge service`() {
        mockMvc
            .perform(delete("/api/v1/members/$publicId/permanent").with(role("ADMIN")))
            .andExpect(status().isNoContent)
        verify(memberPurgeService).purgeMember(publicId)
    }
}
