package com.hanmaum.dn.app.features.ministry.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryRegistrationDto
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignmentStatus
import com.hanmaum.dn.app.features.ministry.service.MinistryRegistrationService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(MinistryRegistrationController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MinistryRegistrationControllerTest {
    @Autowired private lateinit var mvc: MockMvc

    @MockitoBean private lateinit var service: MinistryRegistrationService

    @MockitoBean private lateinit var members: MemberRepository

    @MockitoBean private lateinit var decoder: JwtDecoder

    private val ministryId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val pending =
        MinistryRegistrationDto(
            ministryId.toString(),
            "찬양팀",
            Instant.parse("2026-09-25T10:00:00Z"),
            MinistryAssignmentStatus.PENDING,
            true,
        )

    @Test
    fun `my ministries uses the JWT subject and returns application details`() {
        `when`(service.mine("member-sub")).thenReturn(listOf(pending))

        mvc
            .perform(
                get("/api/v1/me/ministries")
                    .with(jwt().jwt { it.subject("member-sub") }.authorities(SimpleGrantedAuthority("ROLE_MEMBER"))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].ministryPublicId").value(ministryId.toString()))
            .andExpect(jsonPath("$.data[0].ministryName").value("찬양팀"))
            .andExpect(jsonPath("$.data[0].appliedAt").value("2026-09-25T10:00:00Z"))
            .andExpect(jsonPath("$.data[0].status").value("PENDING"))
        verify(service).mine("member-sub")
    }

    @Test
    fun `my ministries returns an empty list when the member has no applications`() {
        `when`(service.mine("member-sub")).thenReturn(emptyList())

        mvc
            .perform(
                get("/api/v1/me/ministries")
                    .with(jwt().jwt { it.subject("member-sub") }.authorities(SimpleGrantedAuthority("ROLE_MEMBER"))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data").isEmpty)
        verify(service).mine("member-sub")
    }

    @Test
    fun `member application returns pending and notified state`() {
        `when`(service.apply(ministryId, "저는 찬양을 좋아합니다", "member-sub")).thenReturn(pending)

        mvc
            .perform(
                post("/api/v1/ministries/$ministryId/registrations")
                    .with(jwt().jwt { it.subject("member-sub") }.authorities(SimpleGrantedAuthority("ROLE_MEMBER")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"selfIntroduction":"저는 찬양을 좋아합니다"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.leaderNotified").value(true))
        verify(service).apply(ministryId, "저는 찬양을 좋아합니다", "member-sub")
    }

    @Test
    fun `blank introduction is rejected before service call`() {
        mvc
            .perform(
                post("/api/v1/ministries/$ministryId/registrations")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_MEMBER")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"selfIntroduction":" "}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `ordinary member cannot access pending team applications`() {
        mvc
            .perform(
                get("/api/v1/ministries/$ministryId/applications")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_MEMBER"))),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `leader can send a rejection message`() {
        `when`(
            service.review(
                ministryId,
                memberId,
                com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryRegistrationDecision.REJECT,
                "다음에 다시 신청해 주세요",
                "leader-sub",
                false,
            ),
        ).thenReturn(pending.copy(status = MinistryAssignmentStatus.REJECTED, rejectionMessage = "다음에 다시 신청해 주세요"))

        mvc
            .perform(
                patch("/api/v1/ministries/$ministryId/applications/$memberId")
                    .with(jwt().jwt { it.subject("leader-sub") }.authorities(SimpleGrantedAuthority("ROLE_MINISTRY_LEADER")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"decision":"REJECT","message":"다음에 다시 신청해 주세요"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.rejectionMessage").value("다음에 다시 신청해 주세요"))
    }
}
