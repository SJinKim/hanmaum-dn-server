package com.hanmaum.dn.app.features.church.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.church.api.v1.dto.ChurchLocationResponse
import com.hanmaum.dn.app.features.church.service.ChurchLocationService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import kotlin.test.Test

@WebMvcTest(ChurchLocationController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class ChurchLocationControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var churchLocationService: ChurchLocationService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `GET church location returns client compatible top-level response`() {
        `when`(churchLocationService.getLocation()).thenReturn(
            ChurchLocationResponse(
                latitude = 51.1234,
                longitude = 6.5678,
                radiusMeters = 100,
            ),
        )

        mockMvc
            .perform(
                get("/api/v1/church/location")
                    .with(jwt().jwt { it.subject("kc-001") }),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.latitude").value(51.1234))
            .andExpect(jsonPath("$.longitude").value(6.5678))
            .andExpect(jsonPath("$.radiusMeters").value(100))
            .andExpect(jsonPath("$.data").doesNotExist())
    }

    @Test
    fun `GET church location requires authentication`() {
        mockMvc
            .perform(get("/api/v1/church/location"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET church location returns 503 with the error shape while the deployment is unconfigured`() {
        `when`(churchLocationService.getLocation()).thenThrow(
            ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Church location is not configured."),
        )

        mockMvc
            .perform(
                get("/api/v1/church/location")
                    .with(jwt().jwt { it.subject("kc-001") }),
            ).andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.error").value("Service Unavailable"))
            .andExpect(jsonPath("$.message").value("Church location is not configured."))
    }
}
