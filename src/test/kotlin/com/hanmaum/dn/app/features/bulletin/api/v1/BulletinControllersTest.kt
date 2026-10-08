package com.hanmaum.dn.app.features.bulletin.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.bulletin.service.BulletinEditionService
import com.hanmaum.dn.app.features.bulletin.service.BulletinSettingsService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.Test

/** HDN-146: editing and settings are admin-only, reading the published 주보 needs any login. */
@WebMvcTest(
    controllers = [BulletinAdminController::class, BulletinSettingsController::class, BulletinController::class],
    excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class],
)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class BulletinControllersTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var editions: BulletinEditionService

    @MockitoBean private lateinit var settings: BulletinSettingsService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val editionId: UUID = UUID.randomUUID()

    private fun withRole(role: String?) =
        jwt()
            .jwt { it.subject("kc-002") }
            .authorities(listOfNotNull(role).map { SimpleGrantedAuthority("ROLE_$it") })

    @Test
    fun `every bulletin endpoint requires authentication`() {
        for (path in listOf("/api/v1/bulletins/current", "/api/v1/admin/bulletins", "/api/v1/admin/bulletin/services")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized)
        }
        verifyNoInteractions(editions, settings)
    }

    @Test
    fun `admin endpoints are forbidden for a plain member and a group leader`() {
        for (role in listOf(null, "GROUP_LEADER")) {
            mockMvc.perform(get("/api/v1/admin/bulletins").with(withRole(role))).andExpect(status().isForbidden)
            mockMvc.perform(post("/api/v1/admin/bulletins/$editionId/publish").with(withRole(role))).andExpect(status().isForbidden)
            mockMvc.perform(get("/api/v1/admin/bulletin/section-titles").with(withRole(role))).andExpect(status().isForbidden)
        }
        verifyNoInteractions(editions, settings)
    }

    @Test
    fun `an admin publishes with the token's subject`() {
        mockMvc.perform(post("/api/v1/admin/bulletins/$editionId/publish").with(withRole("ADMIN"))).andExpect(status().isOk)
        verify(editions).publish(editionId, "kc-002")
    }

    @Test
    fun `deleting a draft is 204`() {
        mockMvc.perform(delete("/api/v1/admin/bulletins/$editionId").with(withRole("ADMIN"))).andExpect(status().isNoContent)
        verify(editions).delete(editionId)
    }

    @Test
    fun `deleting a used service is 200, an unused one 204`() {
        val serviceId = UUID.randomUUID()
        `when`(settings.deleteService(serviceId, "kc-002")).thenReturn(null)
        mockMvc
            .perform(delete("/api/v1/admin/bulletin/services/$serviceId").with(withRole("ADMIN")))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `a member sees 404 while nothing is published`() {
        `when`(editions.currentView()).thenReturn(null)
        mockMvc.perform(get("/api/v1/bulletins/current").with(withRole(null))).andExpect(status().isNotFound)
    }
}
