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
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
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
                latitude = 50.1281518,
                longitude = 8.5843494,
                radiusMeters = 100,
            ),
        )

        mockMvc
            .perform(
                get("/api/v1/church/location")
                    .with(jwt().jwt { it.subject("kc-001") }),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.latitude").value(50.1281518))
            .andExpect(jsonPath("$.longitude").value(8.5843494))
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
    fun `GET unknown API route returns not found instead of internal server error`() {
        mockMvc
            .perform(
                get("/api/v1/does-not-exist")
                    .with(jwt().jwt { it.subject("kc-001") }),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
    }
}
