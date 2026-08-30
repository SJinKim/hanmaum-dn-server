package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.attendance.api.v1.dto.MemberAttendanceEntryResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.MemberAttendanceHistoryResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.MemberAttendanceSummaryResponse
import com.hanmaum.dn.app.features.attendance.service.MemberAttendanceService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test

@WebMvcTest(MemberAttendanceController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MemberAttendanceControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var memberAttendanceService: MemberAttendanceService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val definitionId = UUID.randomUUID()
    private val today = LocalDate.of(2026, 8, 30)

    private fun memberToken() =
        jwt()
            .jwt { it.subject("kc-001") }
            .authorities(SimpleGrantedAuthority("ROLE_MEMBER"))

    private fun sampleHistory() =
        MemberAttendanceHistoryResponse(
            from = LocalDate.of(2026, 8, 1),
            to = today,
            entries =
                listOf(
                    MemberAttendanceEntryResponse(
                        definitionPublicId = definitionId.toString(),
                        definitionTitle = "주일예배",
                        date = today,
                        checkedIn = false,
                        checkedInAt = null,
                    ),
                    MemberAttendanceEntryResponse(
                        definitionPublicId = definitionId.toString(),
                        definitionTitle = "주일예배",
                        date = LocalDate.of(2026, 8, 23),
                        checkedIn = true,
                        checkedInAt = Instant.parse("2026-08-23T08:05:00Z"),
                    ),
                ),
        )

    // ─── GET /me/attendance ───────────────────────────────────────────────────

    @Test
    fun `GET me-attendance returns the history with the resolved range`() {
        `when`(memberAttendanceService.getHistory(eq("kc-001"), eq(null), eq(null))).thenReturn(sampleHistory())

        mockMvc
            .perform(get("/api/v1/me/attendance").with(memberToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.from").value("2026-08-01"))
            .andExpect(jsonPath("$.data.to").value("2026-08-30"))
            .andExpect(jsonPath("$.data.entries[0].checkedIn").value(false))
            .andExpect(jsonPath("$.data.entries[0].checkedInAt").doesNotExist())
            .andExpect(jsonPath("$.data.entries[1].checkedIn").value(true))
            .andExpect(jsonPath("$.data.entries[1].definitionTitle").value("주일예배"))
    }

    @Test
    fun `GET me-attendance passes the date range through`() {
        `when`(
            memberAttendanceService.getHistory(eq("kc-001"), eq(LocalDate.of(2026, 8, 1)), eq(today)),
        ).thenReturn(sampleHistory())

        mockMvc
            .perform(
                get("/api/v1/me/attendance")
                    .param("from", "2026-08-01")
                    .param("to", "2026-08-30")
                    .with(memberToken()),
            ).andExpect(status().isOk)

        verify(memberAttendanceService).getHistory("kc-001", LocalDate.of(2026, 8, 1), today)
    }

    @Test
    fun `GET me-attendance resolves the member from the token, never from a parameter`() {
        `when`(memberAttendanceService.getHistory(eq("kc-001"), eq(null), eq(null))).thenReturn(sampleHistory())

        // A caller trying to read someone else's history gets their own back: the
        // parameter is not bound to anything.
        mockMvc
            .perform(get("/api/v1/me/attendance").param("memberId", "kc-someone-else").with(memberToken()))
            .andExpect(status().isOk)

        verify(memberAttendanceService).getHistory("kc-001", null, null)
    }

    @Test
    fun `GET me-attendance surfaces an invalid range as 400`() {
        `when`(memberAttendanceService.getHistory(eq("kc-001"), eq(today), eq(LocalDate.of(2026, 8, 1))))
            .thenThrow(ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다."))

        mockMvc
            .perform(
                get("/api/v1/me/attendance")
                    .param("from", "2026-08-30")
                    .param("to", "2026-08-01")
                    .with(memberToken()),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `GET me-attendance rejects an unparseable date`() {
        mockMvc
            .perform(get("/api/v1/me/attendance").param("from", "yesterday").with(memberToken()))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET me-attendance returns 401 without a token`() {
        mockMvc.perform(get("/api/v1/me/attendance")).andExpect(status().isUnauthorized)
    }

    // ─── GET /me/attendance/summary ───────────────────────────────────────────

    @Test
    fun `GET me-attendance-summary returns the tile counters`() {
        `when`(memberAttendanceService.getSummary("kc-001"))
            .thenReturn(
                MemberAttendanceSummaryResponse(
                    monthAttended = 3,
                    monthTotal = 5,
                    yearAttended = 24,
                    yearToDateTotal = 35,
                    rate = 0.686,
                ),
            )

        mockMvc
            .perform(get("/api/v1/me/attendance/summary").with(memberToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.monthAttended").value(3))
            .andExpect(jsonPath("$.data.monthTotal").value(5))
            .andExpect(jsonPath("$.data.yearAttended").value(24))
            .andExpect(jsonPath("$.data.yearToDateTotal").value(35))
            .andExpect(jsonPath("$.data.rate").value(0.686))
    }

    @Test
    fun `GET me-attendance-summary returns 401 without a token`() {
        mockMvc.perform(get("/api/v1/me/attendance/summary")).andExpect(status().isUnauthorized)
    }
}
