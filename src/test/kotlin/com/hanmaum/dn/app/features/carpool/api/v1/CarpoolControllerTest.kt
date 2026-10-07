package com.hanmaum.dn.app.features.carpool.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.carpool.service.CarpoolService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.Test

/** #286: the caller is always the token's subject, never a client-supplied member. */
@WebMvcTest(CarpoolController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class CarpoolControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var carpoolService: CarpoolService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val carId: UUID = UUID.randomUUID()

    @Test
    fun `join requires authentication`() {
        mockMvc.perform(post("/api/v1/carpool/$carId/join")).andExpect(status().isUnauthorized)
        verifyNoInteractions(carpoolService)
    }

    @Test
    fun `join seats the token's subject`() {
        mockMvc
            .perform(post("/api/v1/carpool/$carId/join").with(jwt().jwt { it.subject("kc-002") }))
            .andExpect(status().isOk)
        verify(carpoolService).joinCar(carId, "kc-002")
    }

    @Test
    fun `create passes the admin flag from the token`() {
        `when`(carpoolService.createCar(any(), eq("kc-001"), eq(true))).thenReturn(UUID.randomUUID())

        mockMvc
            .perform(
                post("/api/v1/carpool")
                    .with(jwt().jwt { it.subject("kc-001") }.authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"sessionDate":"2026-10-11","name":"테스트 차","maxSeats":4}"""),
            ).andExpect(status().isCreated)
        verify(carpoolService).createCar(any(), eq("kc-001"), eq(true))
    }
}
