package com.hanmaum.dn.app.features.ministry.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.api.v1.dto.ActiveMinistryMemberDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.CreateMinistryRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.UpdateMinistryMemberRequest
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignmentRole
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignmentStatus
import com.hanmaum.dn.app.features.ministry.service.MinistryService
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.argumentCaptor
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test

/**
 * Role enforcement for POST /api/v1/ministries/{publicId}/members (the "맴버 추가" action).
 * Imports SecurityConfig so @PreAuthorize is actually evaluated (cf. EventRsvpControllerTest).
 */
@WebMvcTest(MinistryController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MinistryMemberControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var ministryService: MinistryService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val ministryId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()

    private fun sampleDto() =
        ActiveMinistryMemberDto(
            publicId = memberId.toString(),
            fullName = "김철수",
            startDate = "2026-06-01",
            note = null,
            gender = null,
        )

    private val body = """{"memberId":"$memberId"}"""

    @Test
    fun `POST add member returns 201 for admin`() {
        `when`(ministryService.addMember(eq(ministryId), any())).thenReturn(sampleDto())

        mockMvc
            .perform(
                post("/api/v1/ministries/$ministryId/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.publicId").value(memberId.toString()))
    }

    @Test
    fun `POST add member returns 201 for ministry leader`() {
        `when`(ministryService.addMember(eq(ministryId), any())).thenReturn(sampleDto())

        mockMvc
            .perform(
                post("/api/v1/ministries/$ministryId/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_MINISTRY_LEADER"))),
            ).andExpect(status().isCreated)
    }

    @Test
    fun `POST add member returns 403 for plain member`() {
        mockMvc
            .perform(
                post("/api/v1/ministries/$ministryId/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(jwt()),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `GET members includes ended history and assignment fields when requested`() {
        `when`(ministryService.getActiveMembers(ministryId, true)).thenReturn(
            listOf(
                sampleDto().copy(
                    role = MinistryAssignmentRole.SUB_LEADER,
                    status = MinistryAssignmentStatus.PENDING,
                    endDate = "2026-07-03",
                ),
            ),
        )

        mockMvc
            .perform(get("/api/v1/ministries/$ministryId/members?includeEnded=true").with(jwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].role").value("SUB_LEADER"))
            .andExpect(jsonPath("$.data[0].status").value("PENDING"))
            .andExpect(jsonPath("$.data[0].endDate").value("2026-07-03"))
        verify(ministryService).getActiveMembers(ministryId, true)
    }

    @Test
    fun `PATCH member accepts role status dates and note for admin`() {
        `when`(ministryService.updateMember(eq(ministryId), eq(memberId), any())).thenReturn(sampleDto())

        mockMvc
            .perform(
                patch("/api/v1/ministries/$ministryId/members/$memberId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"role":"SUB_LEADER","status":"PENDING","startDate":"2026-02-01","endDate":"2026-07-03","note":"updated"}""",
                    ).with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isOk)

        val request = argumentCaptor<UpdateMinistryMemberRequest>()
        verify(ministryService).updateMember(eq(ministryId), eq(memberId), request.capture())
        kotlin.test.assertEquals(MinistryAssignmentRole.SUB_LEADER, request.firstValue.role)
        kotlin.test.assertEquals(MinistryAssignmentStatus.PENDING, request.firstValue.status)
        kotlin.test.assertEquals(LocalDate.of(2026, 7, 3), request.firstValue.endDate)
    }

    @Test
    fun `DELETE member ends assignment for admin`() {
        mockMvc
            .perform(
                delete("/api/v1/ministries/$ministryId/members/$memberId")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isNoContent)
        verify(ministryService).removeMember(ministryId, memberId)
    }

    @Test
    fun `PATCH and DELETE member reject a plain member`() {
        mockMvc
            .perform(
                patch("/api/v1/ministries/$ministryId/members/$memberId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
                    .with(jwt()),
            ).andExpect(status().isForbidden)
        mockMvc
            .perform(delete("/api/v1/ministries/$ministryId/members/$memberId").with(jwt()))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `PATCH and DELETE member reject a ministry leader`() {
        val leader = jwt().authorities(SimpleGrantedAuthority("ROLE_MINISTRY_LEADER"))
        mockMvc
            .perform(
                patch("/api/v1/ministries/$ministryId/members/$memberId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
                    .with(leader),
            ).andExpect(status().isForbidden)
        mockMvc
            .perform(delete("/api/v1/ministries/$ministryId/members/$memberId").with(leader))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `POST ministry accepts inactive state and leader member`() {
        val description = "x".repeat(300)
        `when`(ministryService.createMinistry(any())).thenReturn(
            MinistryDto(
                publicId = ministryId.toString(),
                title = "New",
                subtitle = "Description",
                about = "About",
                requirements = emptyList(),
                schedules = emptyList(),
                contacts = emptyList(),
                imageUrl = null,
                isActive = false,
                leaderPublicId = memberId.toString(),
                leaderName = "김철수",
            ),
        )

        mockMvc
            .perform(
                post("/api/v1/ministries")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"New","subtitle":"$description","about":"About","isActive":false,"leaderPublicId":"$memberId"}""")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.isActive").value(false))
            .andExpect(jsonPath("$.data.leaderPublicId").value(memberId.toString()))

        val request = argumentCaptor<CreateMinistryRequest>()
        verify(ministryService).createMinistry(request.capture())
        kotlin.test.assertEquals(false, request.firstValue.isActive)
        kotlin.test.assertEquals(memberId, request.firstValue.leaderPublicId)
        kotlin.test.assertEquals(description, request.firstValue.subtitle)
    }

    // ─── Helper: Mockito any()/eq() for non-nullable Kotlin params ────────────
    private fun <T> any(): T = org.mockito.kotlin.any()

    private fun <T> eq(value: T): T = org.mockito.kotlin.eq(value)
}
