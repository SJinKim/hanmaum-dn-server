package com.hanmaum.dn.app.common.security

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.repository.MemberRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.test.Test

@WebMvcTest(
    NewcomerAuthorizationProbeController::class,
    excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class],
)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class NewcomerAuthorizationTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private fun role(name: String) = jwt().authorities(SimpleGrantedAuthority("ROLE_$name"))

    @Test
    fun `anonymous receives a 401 ProblemDetail`() {
        mockMvc
            .perform(get("/security/newcomers"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.status").value(401))
    }

    @Test
    fun `member receives a 403 ProblemDetail for newcomer PII`() {
        mockMvc
            .perform(get("/security/newcomers").with(role("MEMBER")))
            .andExpect(status().isForbidden)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Forbidden"))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.detail").value("Access denied."))

        mockMvc
            .perform(post("/security/newcomers").with(role("MEMBER")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `viewer can read but cannot write newcomer data`() {
        mockMvc
            .perform(get("/security/newcomers").with(role("NEWCOMER_VIEWER")))
            .andExpect(status().isOk)

        mockMvc
            .perform(post("/security/newcomers").with(role("NEWCOMER_VIEWER")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `editor can read and write newcomer data`() {
        mockMvc
            .perform(get("/security/newcomers").with(role("NEWCOMER_EDITOR")))
            .andExpect(status().isOk)

        mockMvc
            .perform(post("/security/newcomers").with(role("NEWCOMER_EDITOR")))
            .andExpect(status().isOk)
    }

    @Test
    fun `admin can read and write newcomer data`() {
        mockMvc
            .perform(get("/security/newcomers").with(role("ADMIN")))
            .andExpect(status().isOk)

        mockMvc
            .perform(post("/security/newcomers").with(role("ADMIN")))
            .andExpect(status().isOk)
    }
}

@RestController
@RequestMapping("/security/newcomers")
private class NewcomerAuthorizationProbeController {
    @GetMapping
    @NewcomerReadAccess
    fun read(): String = "read"

    @PostMapping
    @NewcomerWriteAccess
    fun write(): String = "write"
}
