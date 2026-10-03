package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.common.domainvalue.CheckInPresence
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceLogResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateAttendanceLogRequest
import com.hanmaum.dn.app.features.attendance.service.AttendanceRosterService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test

@WebMvcTest(AttendanceLogController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AttendanceLogControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var rosterService: AttendanceRosterService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val memberId = UUID.randomUUID()
    private val definitionId = UUID.randomUUID()
    private val date = LocalDate.of(2026, 6, 14)

    private fun token(role: String) =
        jwt()
            .jwt { it.subject("kc-001") }
            .authorities(SimpleGrantedAuthority("ROLE_$role"))

    private fun sampleRow(fullName: String? = "홍길동") =
        AttendanceLogResponse(
            logPublicId = UUID.randomUUID().toString(),
            memberPublicId = fullName?.let { memberId.toString() },
            fullName = fullName,
            groupPublicId = UUID.randomUUID().toString(),
            groupName = "1순",
            definitionPublicId = definitionId.toString(),
            definitionTitle = "주일예배",
            checkedInAt = Instant.parse("2026-06-14T08:05:00Z"),
            presence = CheckInPresence.IN_PLACE,
        )

    private val body = """{"memberId":"$memberId","definitionId":"$definitionId","date":"2026-06-14"}"""

    // ─── GET /attendance/logs ────────────────────────────────────────────────

    @Test
    fun `GET logs returns the roster for an admin`() {
        `when`(rosterService.getRoster(date, definitionId)).thenReturn(listOf(sampleRow(), sampleRow(fullName = null)))

        mockMvc
            .perform(
                get("/api/v1/attendance/logs")
                    .param("date", "2026-06-14")
                    .param("definitionId", definitionId.toString())
                    .with(token("ADMIN")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].fullName").value("홍길동"))
            .andExpect(jsonPath("$.data[0].groupName").value("1순"))
            .andExpect(jsonPath("$.data[0].presence").value("IN_PLACE"))
            .andExpect(jsonPath("$.data[0].checkedInAt").value("2026-06-14T08:05:00Z"))
            .andExpect(jsonPath("$.data[1].fullName").doesNotExist())
    }

    @Test
    fun `GET logs without definitionId covers every definition of the day`() {
        `when`(rosterService.getRoster(date, null)).thenReturn(emptyList())

        mockMvc
            .perform(get("/api/v1/attendance/logs").param("date", "2026-06-14").with(token("ADMIN")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isEmpty)
    }

    @Test
    fun `GET logs without a date is a 400`() {
        mockMvc
            .perform(get("/api/v1/attendance/logs").with(token("ADMIN")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET logs is forbidden for a member`() {
        mockMvc
            .perform(get("/api/v1/attendance/logs").param("date", "2026-06-14").with(token("MEMBER")))
            .andExpect(status().isForbidden)

        verifyNoInteractions(rosterService)
    }

    @Test
    fun `GET logs without a token is a 401`() {
        mockMvc
            .perform(get("/api/v1/attendance/logs").param("date", "2026-06-14"))
            .andExpect(status().isUnauthorized)
    }

    // ─── POST /attendance/logs ───────────────────────────────────────────────

    @Test
    fun `POST logs adds a log and answers 201`() {
        `when`(rosterService.addLog(eq(CreateAttendanceLogRequest(memberId, definitionId, date)))).thenReturn(sampleRow())

        mockMvc
            .perform(
                post("/api/v1/attendance/logs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(token("ADMIN")),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.fullName").value("홍길동"))
    }

    @Test
    fun `POST logs for a duplicate answers 409`() {
        `when`(rosterService.addLog(any())).thenThrow(ResponseStatusException(HttpStatus.CONFLICT, "이미 출석이 기록되어 있습니다."))

        mockMvc
            .perform(
                post("/api/v1/attendance/logs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(token("ADMIN")),
            ).andExpect(status().isConflict)
    }

    @Test
    fun `POST logs without memberId is a 400`() {
        mockMvc
            .perform(
                post("/api/v1/attendance/logs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"definitionId":"$definitionId","date":"2026-06-14"}""")
                    .with(token("ADMIN")),
            ).andExpect(status().isBadRequest)

        verifyNoInteractions(rosterService)
    }

    @Test
    fun `POST logs is forbidden for a member`() {
        mockMvc
            .perform(
                post("/api/v1/attendance/logs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(token("MEMBER")),
            ).andExpect(status().isForbidden)

        verifyNoInteractions(rosterService)
    }
}
