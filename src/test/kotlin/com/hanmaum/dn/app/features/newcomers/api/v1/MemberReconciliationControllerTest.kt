package com.hanmaum.dn.app.features.newcomers.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.ReconciliationMemberResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.ReconciliationResponse
import com.hanmaum.dn.app.features.newcomers.domain.ReconciliationStatus
import com.hanmaum.dn.app.features.newcomers.service.MemberReconciliationService
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.Test

@WebMvcTest(MemberReconciliationController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MemberReconciliationControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var service: MemberReconciliationService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private fun token(role: String) = jwt().jwt { it.subject("kc-admin") }.authorities(SimpleGrantedAuthority("ROLE_$role"))

    @Test
    fun `admin and newcomer editor can resolve while viewer can only read`() {
        val reviewId = UUID.randomUUID()
        val selectedId = UUID.randomUUID()
        whenever(service.get(reviewId)).thenReturn(response())
        whenever(service.link(any(), any(), any(), any(), any())).thenReturn(response())
        val request = """{"memberPublicId":"$selectedId","version":0}"""

        mockMvc
            .perform(
                get("/api/v1/newcomers/reconciliations/$reviewId")
                    .with(token("NEWCOMER_VIEWER")),
            ).andExpect(status().isOk)
        mockMvc
            .perform(
                post("/api/v1/newcomers/reconciliations/$reviewId/link")
                    .contentType("application/json")
                    .content(request)
                    .with(token("NEWCOMER_VIEWER")),
            ).andExpect(status().isForbidden)
        mockMvc
            .perform(
                post("/api/v1/newcomers/reconciliations/$reviewId/link")
                    .contentType("application/json")
                    .content(request)
                    .with(token("NEWCOMER_EDITOR")),
            ).andExpect(status().isOk)
        mockMvc
            .perform(
                post("/api/v1/newcomers/reconciliations/$reviewId/link")
                    .contentType("application/json")
                    .content(request)
                    .with(token("ADMIN")),
            ).andExpect(status().isOk)
        mockMvc
            .perform(get("/api/v1/newcomers/reconciliations/$reviewId").with(token("MEMBER")))
            .andExpect(status().isForbidden)
    }

    private fun response() =
        ReconciliationResponse(
            publicId = UUID.randomUUID().toString(),
            status = ReconciliationStatus.OPEN,
            reasons = listOf("MULTIPLE_CANDIDATES"),
            conflictFields = emptyList(),
            registrationMember = member(),
            candidates = listOf(member()),
            selectedMemberPublicId = null,
            version = 0,
            createdAt = null,
            resolvedAt = null,
        )

    private fun member() =
        ReconciliationMemberResponse(
            publicId = UUID.randomUUID().toString(),
            firstName = "새봄",
            lastName = "김",
            email = "private@example.com",
            birthDate = null,
            phoneNumber = null,
            linked = false,
        )
}
