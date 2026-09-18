package com.hanmaum.dn.app.features.newcomers.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerResponse
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerLifecycle
import com.hanmaum.dn.app.features.newcomers.service.NewcomerService
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.Test

@WebMvcTest(NewcomerController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class NewcomerControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var service: NewcomerService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private fun token(role: String) = jwt().authorities(SimpleGrantedAuthority("ROLE_$role"))

    private fun response() =
        NewcomerResponse(
            publicId = UUID.randomUUID().toString(),
            memberPublicId = UUID.randomUUID().toString(),
            lastName = "김",
            firstName = "새봄",
            hasVisited = false,
            lifecycleStatus = NewcomerLifecycle.SUBMITTED,
            version = 0,
            createdAt = null,
            updatedAt = null,
        )

    @Test
    fun `viewer can read but cannot create`() {
        whenever(service.get(any())).thenReturn(response())
        mockMvc.perform(get("/api/v1/newcomers/${UUID.randomUUID()}").with(token("NEWCOMER_VIEWER"))).andExpect(status().isOk)
        mockMvc
            .perform(
                post("/api/v1/newcomers")
                    .contentType("application/json")
                    .content("""{"lastName":"김","firstName":"새봄"}""")
                    .with(token("NEWCOMER_VIEWER")),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `editor and admin can create while member cannot read`() {
        whenever(service.create(any())).thenReturn(response())
        whenever(service.get(any())).thenReturn(response())
        val body = """{"lastName":"김","firstName":"새봄"}"""
        mockMvc
            .perform(post("/api/v1/newcomers").contentType("application/json").content(body).with(token("NEWCOMER_EDITOR")))
            .andExpect(status().isCreated)
        mockMvc
            .perform(post("/api/v1/newcomers").contentType("application/json").content(body).with(token("ADMIN")))
            .andExpect(status().isCreated)
        mockMvc.perform(get("/api/v1/newcomers/${UUID.randomUUID()}").with(token("NEWCOMER_EDITOR"))).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/newcomers/${UUID.randomUUID()}").with(token("ADMIN"))).andExpect(status().isOk)
        mockMvc.perform(get("/api/v1/newcomers/${UUID.randomUUID()}").with(token("MEMBER"))).andExpect(status().isForbidden)
    }

    @Test
    fun `invalid controlled value returns problem detail without echoing PII`() {
        mockMvc
            .perform(
                post("/api/v1/newcomers")
                    .contentType("application/json")
                    .content("""{"lastName":"Private","firstName":"Person","gender":"INVALID"}""")
                    .with(token("NEWCOMER_EDITOR")),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.detail").value("A newcomer field contains an invalid value."))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Private"))))
    }

    @Test
    fun `unauthenticated caller receives problem detail`() {
        mockMvc
            .perform(get("/api/v1/newcomers/${UUID.randomUUID()}"))
            .andExpect(status().isUnauthorized)
    }
}
