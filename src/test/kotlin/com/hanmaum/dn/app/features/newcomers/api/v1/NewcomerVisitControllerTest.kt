package com.hanmaum.dn.app.features.newcomers.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerVisitResponse
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerVisitType
import com.hanmaum.dn.app.features.newcomers.service.NewcomerVisitService
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test

@WebMvcTest(NewcomerVisitController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class NewcomerVisitControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var service: NewcomerVisitService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val sunday = LocalDate.of(2026, 6, 14)

    private fun token(role: String) =
        jwt()
            .jwt { it.subject("kc-001") }
            .authorities(SimpleGrantedAuthority("ROLE_$role"))

    private fun sampleRow() =
        NewcomerVisitResponse(
            publicId = UUID.randomUUID().toString(),
            visitDate = sunday,
            lastName = "홍",
            firstName = "길동",
            fullName = "홍길동",
            gender = null,
            birthYear = 2000,
            visitType = NewcomerVisitType.FIRST,
            source = null,
            note = null,
            newcomerPublicId = null,
            newcomerLifecycle = null,
            createdAt = Instant.parse("2026-06-14T08:05:00Z"),
        )

    private val body = """{"lastName":"홍","firstName":"길동","birthYear":2000}"""

    // ─── GET ──────────────────────────────────────────────────────────────────

    @Test
    fun `GET visits returns the rows for a newcomer viewer`() {
        `when`(service.list(sunday, sunday)).thenReturn(listOf(sampleRow()))

        mockMvc
            .perform(
                get("/api/v1/newcomers/visits")
                    .param("from", "2026-06-14")
                    .param("to", "2026-06-14")
                    .with(token("NEWCOMER_VIEWER")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].fullName").value("홍길동"))
            .andExpect(jsonPath("$.data[0].visitDate").value("2026-06-14"))
    }

    @Test
    fun `GET visits is forbidden for a member`() {
        mockMvc
            .perform(get("/api/v1/newcomers/visits").with(token("MEMBER")))
            .andExpect(status().isForbidden)

        verifyNoInteractions(service)
    }

    @Test
    fun `GET visits without a token is a 401`() {
        mockMvc.perform(get("/api/v1/newcomers/visits")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET stats is not taken for a newcomer publicId`() {
        `when`(service.stats(null, null)).thenReturn(null)

        mockMvc
            .perform(get("/api/v1/newcomers/visits/stats").with(token("ADMIN")))
            .andExpect(status().isOk)

        verify(service).stats(null, null)
    }

    // ─── POST ─────────────────────────────────────────────────────────────────

    @Test
    fun `POST visits records a visit and answers 201`() {
        `when`(service.create(any())).thenReturn(sampleRow())

        mockMvc
            .perform(
                post("/api/v1/newcomers/visits")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(token("NEWCOMER_EDITOR")),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.fullName").value("홍길동"))
    }

    @Test
    fun `POST visits without a first name is a 400`() {
        mockMvc
            .perform(
                post("/api/v1/newcomers/visits")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"lastName":"홍","firstName":" "}""")
                    .with(token("ADMIN")),
            ).andExpect(status().isBadRequest)

        verifyNoInteractions(service)
    }

    @Test
    fun `POST visits is forbidden for a newcomer viewer`() {
        mockMvc
            .perform(
                post("/api/v1/newcomers/visits")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(token("NEWCOMER_VIEWER")),
            ).andExpect(status().isForbidden)

        verifyNoInteractions(service)
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    @Test
    fun `DELETE visit answers 204`() {
        val id = UUID.randomUUID()

        mockMvc
            .perform(delete("/api/v1/newcomers/visits/$id").with(token("ADMIN")))
            .andExpect(status().isNoContent)

        verify(service).softDelete(id)
    }
}
