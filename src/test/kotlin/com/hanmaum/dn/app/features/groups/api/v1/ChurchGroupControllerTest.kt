package com.hanmaum.dn.app.features.groups.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.groups.api.v1.dto.AssignGroupLeaderRequest
import com.hanmaum.dn.app.features.groups.api.v1.dto.ChurchGroupSummaryDto
import com.hanmaum.dn.app.features.groups.service.ChurchGroupService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test

/**
 * Routing, role enforcement and JSON shape for /api/v1/church-groups.
 * Imports SecurityConfig so @PreAuthorize is actually evaluated.
 */
@WebMvcTest(ChurchGroupController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class ChurchGroupControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var objectMapper: ObjectMapper

    @MockitoBean private lateinit var churchGroupService: ChurchGroupService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val groupPublicId: UUID = UUID.randomUUID()
    private val memberPublicId: UUID = UUID.randomUUID()

    private fun ledGroup() =
        ChurchGroupSummaryDto(
            publicId = groupPublicId.toString(),
            division = "1구역",
            name = "다니엘조",
            leaderPublicId = memberPublicId.toString(),
            leaderName = "박민수",
            leaderSince = LocalDate.of(2026, 1, 15),
        )

    private fun admin() = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    @Test
    fun `GET church-groups exposes the leader fields to admin`() {
        `when`(churchGroupService.getGroups()).thenReturn(listOf(ledGroup()))

        mockMvc
            .perform(get("/api/v1/church-groups").with(admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].name").value("다니엘조"))
            .andExpect(jsonPath("$.data[0].leaderPublicId").value(memberPublicId.toString()))
            .andExpect(jsonPath("$.data[0].leaderName").value("박민수"))
            .andExpect(jsonPath("$.data[0].leaderSince").value("2026-01-15"))
    }

    @Test
    fun `GET church-groups returns null leader fields for a vacant group`() {
        `when`(churchGroupService.getGroups())
            .thenReturn(listOf(ChurchGroupSummaryDto(groupPublicId.toString(), null, "새가족")))

        mockMvc
            .perform(get("/api/v1/church-groups").with(admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].leaderPublicId").doesNotExist())
            .andExpect(jsonPath("$.data[0].leaderName").doesNotExist())
    }

    @Test
    fun `PUT leader returns 200 and the updated group for admin`() {
        `when`(churchGroupService.assignLeader(eq(groupPublicId), any()))
            .thenReturn(ledGroup())

        mockMvc
            .perform(
                put("/api/v1/church-groups/$groupPublicId/leader")
                    .with(admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(AssignGroupLeaderRequest(memberPublicId.toString()))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.leaderPublicId").value(memberPublicId.toString()))
            .andExpect(jsonPath("$.data.leaderSince").value("2026-01-15"))
    }

    @Test
    fun `PUT leader returns 403 for a plain member`() {
        mockMvc
            .perform(
                put("/api/v1/church-groups/$groupPublicId/leader")
                    .with(jwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(AssignGroupLeaderRequest(memberPublicId.toString()))),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `PUT leader returns 400 when memberPublicId is blank`() {
        mockMvc
            .perform(
                put("/api/v1/church-groups/$groupPublicId/leader")
                    .with(admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(AssignGroupLeaderRequest(""))),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `PUT leader surfaces a domain rejection as 400, not 500`() {
        `when`(churchGroupService.assignLeader(eq(groupPublicId), any()))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "그룹 리더는 해당 그룹의 맴버여야 합니다."))

        mockMvc
            .perform(
                put("/api/v1/church-groups/$groupPublicId/leader")
                    .with(admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(AssignGroupLeaderRequest(memberPublicId.toString()))),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `DELETE leader returns 200 and a vacant group for admin`() {
        `when`(churchGroupService.clearLeader(eq(groupPublicId)))
            .thenReturn(ChurchGroupSummaryDto(groupPublicId.toString(), "1구역", "다니엘조"))

        mockMvc
            .perform(delete("/api/v1/church-groups/$groupPublicId/leader").with(admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.publicId").value(groupPublicId.toString()))
            .andExpect(jsonPath("$.data.leaderPublicId").doesNotExist())
            .andExpect(jsonPath("$.message").value("그룹 리더가 해제되었습니다."))
    }

    @Test
    fun `DELETE leader returns 403 for a plain member`() {
        mockMvc
            .perform(delete("/api/v1/church-groups/$groupPublicId/leader").with(jwt()))
            .andExpect(status().isForbidden)
    }

    private fun noteTaker() = jwt().authorities(SimpleGrantedAuthority("ROLE_NOTE_TAKER"))

    @Test
    fun `GET church-groups is closed to a note_taker since HDN-299`() {
        mockMvc
            .perform(get("/api/v1/church-groups").with(noteTaker()))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `PUT leader returns 403 for a note_taker`() {
        mockMvc
            .perform(
                put("/api/v1/church-groups/$groupPublicId/leader")
                    .with(noteTaker())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(AssignGroupLeaderRequest(memberPublicId.toString()))),
            ).andExpect(status().isForbidden)
    }

    @ParameterizedTest
    @ValueSource(strings = ["TRAINING_VIEWER", "TRAINING_EDITOR", "ADMIN", "PASTOR"])
    fun `training readers can read the group matrix`(role: String) {
        `when`(churchGroupService.getGroups()).thenReturn(listOf(ledGroup()))
        mockMvc
            .perform(get("/api/v1/church-groups").with(jwt().authorities(SimpleGrantedAuthority("ROLE_$role"))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].publicId").value(groupPublicId.toString()))
            .andExpect(jsonPath("$.data[0].leaderPublicId").value(memberPublicId.toString()))
        verify(churchGroupService).getGroups()
    }

    @ParameterizedTest
    @ValueSource(strings = ["TRAINING_EDITOR", "ADMIN", "PASTOR"])
    fun `training editors can assign and remove the sitting leader`(role: String) {
        `when`(churchGroupService.assignLeader(eq(groupPublicId), any())).thenReturn(ledGroup())
        `when`(churchGroupService.clearLeader(groupPublicId)).thenReturn(ChurchGroupSummaryDto(groupPublicId.toString(), "1구역", "다니엘조"))
        val token = jwt().authorities(SimpleGrantedAuthority("ROLE_$role"))
        mockMvc
            .perform(
                put("/api/v1/church-groups/$groupPublicId/leader")
                    .with(token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(AssignGroupLeaderRequest(memberPublicId.toString()))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.leaderPublicId").value(memberPublicId.toString()))
        mockMvc
            .perform(delete("/api/v1/church-groups/$groupPublicId/leader").with(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.leaderPublicId").doesNotExist())
        val request = argumentCaptor<AssignGroupLeaderRequest>()
        verify(churchGroupService).assignLeader(eq(groupPublicId), request.capture())
        kotlin.test.assertEquals(memberPublicId.toString(), request.firstValue.memberPublicId)
        verify(churchGroupService).clearLeader(groupPublicId)
    }

    @Test
    fun `a training viewer cannot assign or remove a leader`() {
        val token = jwt().authorities(SimpleGrantedAuthority("ROLE_TRAINING_VIEWER"))
        mockMvc
            .perform(
                put("/api/v1/church-groups/$groupPublicId/leader")
                    .with(token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(AssignGroupLeaderRequest(memberPublicId.toString()))),
            ).andExpect(status().isForbidden)
        mockMvc.perform(delete("/api/v1/church-groups/$groupPublicId/leader").with(token)).andExpect(status().isForbidden)
        verifyNoInteractions(churchGroupService)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "NOTE_TAKER",
            "GROUP_LEADER",
            "MINISTRY_LEADER",
            "GENERAL_OPERATIONS_VIEWER",
            "GENERAL_OPERATIONS_EDITOR",
            "NEWCOMER_VIEWER",
            "NEWCOMER_EDITOR",
            "MEMBER",
        ],
    )
    fun `other roles do not gain church group access`(role: String) {
        val token = jwt().authorities(SimpleGrantedAuthority("ROLE_$role"))
        mockMvc.perform(get("/api/v1/church-groups").with(token)).andExpect(status().isForbidden)
        mockMvc
            .perform(
                put("/api/v1/church-groups/$groupPublicId/leader")
                    .with(token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(AssignGroupLeaderRequest(memberPublicId.toString()))),
            ).andExpect(status().isForbidden)
        mockMvc.perform(delete("/api/v1/church-groups/$groupPublicId/leader").with(token)).andExpect(status().isForbidden)
        verifyNoInteractions(churchGroupService)
    }

    @Test
    fun `church group endpoints require authentication`() {
        mockMvc.perform(get("/api/v1/church-groups")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(
                put("/api/v1/church-groups/$groupPublicId/leader")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(AssignGroupLeaderRequest(memberPublicId.toString()))),
            ).andExpect(status().isUnauthorized)
        mockMvc.perform(delete("/api/v1/church-groups/$groupPublicId/leader")).andExpect(status().isUnauthorized)
        verifyNoInteractions(churchGroupService)
    }
}
