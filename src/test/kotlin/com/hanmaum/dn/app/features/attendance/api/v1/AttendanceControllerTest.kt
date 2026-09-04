package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.common.domainvalue.CheckInPresence
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceCheckInResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceGroupCountsResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.ChurchGroupAttendanceCountResponse
import com.hanmaum.dn.app.features.attendance.service.AttendanceService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.mockito.Mockito.`when`
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test

@WebMvcTest(AttendanceController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AttendanceControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var attendanceService: AttendanceService

    @MockitoBean
    private lateinit var memberRepository: MemberRepository

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `POST check-in returns no member identity or exact timestamp`() {
        val date = LocalDate.of(2026, 6, 14)
        val definitionId = UUID.randomUUID()
        `when`(attendanceService.checkIn(eq("kc-001"), anyOrNull()))
            .thenReturn(
                AttendanceCheckInResponse(
                    definitionPublicId = definitionId.toString(),
                    definitionTitle = "주일예배",
                    attendanceDate = date,
                    presence = CheckInPresence.UNCONFIRMED,
                ),
            )

        mockMvc
            .perform(
                post("/api/v1/attendance/check-in")
                    .with(jwt().jwt { it.subject("kc-001") }),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.definitionPublicId").value(definitionId.toString()))
            .andExpect(jsonPath("$.data.attendanceDate").value("2026-06-14"))
            .andExpect(jsonPath("$.data.memberPublicId").doesNotExist())
            .andExpect(jsonPath("$.data.memberName").doesNotExist())
            .andExpect(jsonPath("$.data.createdAt").doesNotExist())
    }

    @Test
    fun `GET group-counts returns only aggregate church-group data`() {
        val date = LocalDate.of(2026, 6, 14)
        val definitionId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        `when`(attendanceService.getGroupCounts(definitionId, date))
            .thenReturn(
                AttendanceGroupCountsResponse(
                    definitionPublicId = definitionId.toString(),
                    definitionTitle = "주일예배",
                    attendanceDate = date,
                    totalCount = 4,
                    totalInPlaceCount = 3,
                    totalOutsideCount = 1,
                    totalUnconfirmedCount = 0,
                    groups =
                        listOf(
                            ChurchGroupAttendanceCountResponse(
                                groupPublicId = groupId.toString(),
                                groupDivision = "청년부",
                                groupName = "다니엘조",
                                attendanceCount = 4,
                                inPlaceCount = 3,
                                outsideCount = 1,
                                unconfirmedCount = 0,
                            ),
                        ),
                ),
            )

        mockMvc
            .perform(
                get("/api/v1/attendance/group-counts")
                    .param("definitionId", definitionId.toString())
                    .param("date", "2026-06-14")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(4))
            .andExpect(jsonPath("$.data.groups[0].groupName").value("다니엘조"))
            .andExpect(jsonPath("$.data.groups[0].attendanceCount").value(4))
            .andExpect(jsonPath("$.data.groups[0].memberPublicId").doesNotExist())
    }

    @Test
    fun `POST check-in accepts a position and echoes the server's verdict`() {
        val date = LocalDate.of(2026, 6, 14)
        `when`(attendanceService.checkIn(eq("kc-001"), anyOrNull()))
            .thenReturn(
                AttendanceCheckInResponse(
                    definitionPublicId = UUID.randomUUID().toString(),
                    definitionTitle = "주일예배",
                    attendanceDate = date,
                    presence = CheckInPresence.IN_PLACE,
                ),
            )

        mockMvc
            .perform(
                post("/api/v1/attendance/check-in")
                    .with(jwt().jwt { it.subject("kc-001") })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"latitude":50.1281518,"longitude":8.5843494,"accuracyMeters":12.0}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.presence").value("IN_PLACE"))
    }

    @Test
    fun `POST check-in without a body is still accepted`() {
        val date = LocalDate.of(2026, 6, 14)
        `when`(attendanceService.checkIn(eq("kc-001"), anyOrNull()))
            .thenReturn(
                AttendanceCheckInResponse(
                    definitionPublicId = UUID.randomUUID().toString(),
                    definitionTitle = "주일예배",
                    attendanceDate = date,
                    presence = CheckInPresence.UNCONFIRMED,
                ),
            )

        // Sharing a location stays optional. Refusing the check-in without one would make
        // the geofence a gate, which is exactly what this design avoids.
        mockMvc
            .perform(
                post("/api/v1/attendance/check-in")
                    .with(jwt().jwt { it.subject("kc-001") }),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.presence").value("UNCONFIRMED"))
    }

    @Test
    fun `POST check-in rejects a partial position instead of silently ignoring it`() {
        // Two of three cannot be judged. Treating it as "no position" would hide a client
        // bug behind a plausible-looking UNCONFIRMED row.
        mockMvc
            .perform(
                post("/api/v1/attendance/check-in")
                    .with(jwt().jwt { it.subject("kc-001") })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"latitude":50.1281518,"longitude":8.5843494}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
    }

    @Test
    fun `GET group-counts splits each group into in-place, outside and unconfirmed`() {
        val date = LocalDate.of(2026, 6, 14)
        val definitionId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        `when`(attendanceService.getGroupCounts(definitionId, date))
            .thenReturn(
                AttendanceGroupCountsResponse(
                    definitionPublicId = definitionId.toString(),
                    definitionTitle = "주일예배",
                    attendanceDate = date,
                    totalCount = 6,
                    totalInPlaceCount = 3,
                    totalOutsideCount = 2,
                    totalUnconfirmedCount = 1,
                    groups =
                        listOf(
                            ChurchGroupAttendanceCountResponse(
                                groupPublicId = groupId.toString(),
                                groupDivision = "청년부",
                                groupName = "다니엘조",
                                attendanceCount = 6,
                                inPlaceCount = 3,
                                outsideCount = 2,
                                unconfirmedCount = 1,
                            ),
                        ),
                ),
            )

        // The web dashboard reads these per group. outside and unconfirmed stay separate on
        // the wire so a view can add them up; it could never take them apart again.
        mockMvc
            .perform(
                get("/api/v1/attendance/group-counts")
                    .param("definitionId", definitionId.toString())
                    .param("date", date.toString())
                    .with(jwt().jwt { it.subject("kc-001") }.authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(6))
            .andExpect(jsonPath("$.data.totalInPlaceCount").value(3))
            .andExpect(jsonPath("$.data.totalOutsideCount").value(2))
            .andExpect(jsonPath("$.data.totalUnconfirmedCount").value(1))
            .andExpect(jsonPath("$.data.groups[0].inPlaceCount").value(3))
            .andExpect(jsonPath("$.data.groups[0].outsideCount").value(2))
            .andExpect(jsonPath("$.data.groups[0].unconfirmedCount").value(1))
    }
}
