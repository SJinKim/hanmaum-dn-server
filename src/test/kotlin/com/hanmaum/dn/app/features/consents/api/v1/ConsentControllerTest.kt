package com.hanmaum.dn.app.features.consents.api.v1

import com.hanmaum.dn.app.common.api.ApiErrorCode
import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.common.domainvalue.ConsentPurpose
import com.hanmaum.dn.app.features.consents.api.v1.dto.ConsentGrant
import com.hanmaum.dn.app.features.consents.api.v1.dto.ConsentPolicyResponse
import com.hanmaum.dn.app.features.consents.api.v1.dto.ConsentState
import com.hanmaum.dn.app.features.consents.api.v1.dto.MemberConsentStatus
import com.hanmaum.dn.app.features.consents.service.ConsentException
import com.hanmaum.dn.app.features.consents.service.ConsentService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberPrincipal
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
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
import kotlin.test.Test

@WebMvcTest(ConsentController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class ConsentControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var consentService: ConsentService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private fun memberToken() = jwt().jwt { it.subject("kc-001") }.authorities(SimpleGrantedAuthority("ROLE_MEMBER"))

    private fun grantBody(
        accepted: Boolean = true,
        version: String = "v1",
    ) = """{"purpose":"APP_PRIVACY","consent":{"accepted":$accepted,"version":"$version"}}"""

    @Test
    fun `GET policies answers without a token`() {
        `when`(consentService.policies()).thenReturn(listOf(ConsentPolicyResponse(ConsentPurpose.APP_PRIVACY, "v1", true)))

        mockMvc
            .perform(get("/api/v1/consents/policies"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].purpose").value("APP_PRIVACY"))
            .andExpect(jsonPath("$.data[0].version").value("v1"))
    }

    @Test
    fun `GET my consents needs a token`() {
        mockMvc.perform(get("/api/v1/members/me/consents")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST records the consent and answers 201`() {
        `when`(consentService.grant(any<MemberPrincipal>(), eq(ConsentPurpose.APP_PRIVACY), eq(ConsentGrant(true, "v1"))))
            .thenReturn(
                MemberConsentStatus(
                    purpose = ConsentPurpose.APP_PRIVACY,
                    state = ConsentState.GRANTED,
                    requiredForAccount = true,
                    currentVersion = "v1",
                    grantedVersion = "v1",
                    grantedAt = Instant.parse("2026-10-22T09:00:00Z"),
                ),
            )

        mockMvc
            .perform(
                post("/api/v1/members/me/consents")
                    .with(memberToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(grantBody()),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.state").value("GRANTED"))
    }

    @Test
    fun `POST with an unticked box is a 400 and records nothing`() {
        mockMvc
            .perform(
                post("/api/v1/members/me/consents")
                    .with(memberToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(grantBody(accepted = false)),
            ).andExpect(status().isBadRequest)

        verifyNoInteractions(consentService)
    }

    @Test
    fun `POST with an outdated version is a 409 carrying the code`() {
        `when`(consentService.grant(any<MemberPrincipal>(), any(), any())).thenThrow(
            ConsentException(HttpStatus.CONFLICT, ApiErrorCode.CONSENT_VERSION_OUTDATED, "outdated"),
        )

        mockMvc
            .perform(
                post("/api/v1/members/me/consents")
                    .with(memberToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(grantBody(version = "v0")),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONSENT_VERSION_OUTDATED"))
    }

    @Test
    fun `DELETE withdraws and answers 204`() {
        mockMvc
            .perform(delete("/api/v1/members/me/consents/APP_PRIVACY").with(memberToken()))
            .andExpect(status().isNoContent)

        verify(consentService).withdraw(any(), eq(ConsentPurpose.APP_PRIVACY))
    }
}
