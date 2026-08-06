package com.hanmaum.dn.app.common.config

import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.test.Test

@WebMvcTest(SecurityProbeController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class SecurityConfigTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var memberRepository: MemberRepository

    @Test
    fun `prometheus actuator endpoint does not require a bearer token`() {
        mockMvc
            .perform(get("/actuator/prometheus"))
            .andExpect(status().isOk)
    }

    @Test
    fun `application endpoints still require authentication`() {
        mockMvc
            .perform(get("/security-probe"))
            .andExpect(status().isUnauthorized)
    }
}

@RestController
private class SecurityProbeController {
    @GetMapping("/security-probe")
    fun probe(): String = "ok"

    @GetMapping("/actuator/prometheus")
    fun prometheus(): String = "metrics"
}
