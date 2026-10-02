package com.hanmaum.dn.app.common.config

import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The anonymous routes of SecurityConfig (#237), each next to a neighbour that must stay
 * protected. The probe controller sits outside `features`, so its paths carry `/api/v1` by hand.
 */
@WebMvcTest(PublicRouteProbeController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class PublicRouteSecurityTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var memberRepository: MemberRepository

    private fun anonymousStatus(
        method: HttpMethod,
        path: String,
    ): Int =
        mockMvc
            .perform(request(method, path))
            .andReturn()
            .response.status

    @Test
    fun `every anonymous route answers without a token`() {
        val public =
            listOf(
                HttpMethod.GET to "/actuator/health",
                HttpMethod.GET to "/actuator/prometheus",
                HttpMethod.GET to "/api/v1/announcements",
                HttpMethod.GET to "/api/v1/albums",
                HttpMethod.GET to "/api/v1/newcomer-forms/some-token",
                HttpMethod.POST to "/api/v1/newcomer-forms/some-token/submissions",
                HttpMethod.POST to "/api/v1/members/register",
            )
        public.forEach { (method, path) -> assertEquals(200, anonymousStatus(method, path), "$method $path") }
    }

    @Test
    fun `the neighbours of the anonymous routes still need a token`() {
        val protected =
            listOf(
                HttpMethod.GET to "/actuator/info",
                HttpMethod.GET to "/api/v1/announcements/admin",
                HttpMethod.GET to "/api/v1/announcements/00000000-0000-0000-0000-000000000001",
                HttpMethod.POST to "/api/v1/announcements",
                HttpMethod.POST to "/api/v1/albums",
                HttpMethod.GET to "/api/v1/newcomer-forms",
                HttpMethod.POST to "/api/v1/newcomer-forms",
                HttpMethod.GET to "/api/v1/members/register",
                HttpMethod.POST to "/api/v1/members",
            )
        protected.forEach { (method, path) -> assertEquals(401, anonymousStatus(method, path), "$method $path") }
    }

    @Test
    fun `self-registration runs through the filter chain and gets the security headers`() {
        mockMvc
            .perform(post("/api/v1/members/register"))
            .andExpect(status().isOk)
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
    }

    @Test
    fun `self-registration applies the CORS policy`() {
        mockMvc
            .perform(
                options("/api/v1/members/register")
                    .header("Origin", "http://localhost:4200")
                    .header("Access-Control-Request-Method", "POST"),
            ).andExpect(status().isOk)
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))

        mockMvc
            .perform(
                options("/api/v1/members/register")
                    .header("Origin", "https://evil.example")
                    .header("Access-Control-Request-Method", "POST"),
            ).andExpect(status().isForbidden)
    }
}

@RestController
private class PublicRouteProbeController {
    @GetMapping("/actuator/health", "/actuator/prometheus", "/actuator/info")
    fun actuator(): String = "ok"

    @GetMapping(
        "/api/v1/announcements",
        "/api/v1/announcements/admin",
        "/api/v1/announcements/{publicId}",
        "/api/v1/albums",
        "/api/v1/newcomer-forms",
        "/api/v1/newcomer-forms/{token}",
        "/api/v1/members/register",
    )
    fun read(): String = "ok"

    @PostMapping(
        "/api/v1/announcements",
        "/api/v1/albums",
        "/api/v1/newcomer-forms",
        "/api/v1/newcomer-forms/{token}/submissions",
        "/api/v1/members/register",
        "/api/v1/members",
    )
    fun write(): String = "ok"
}
