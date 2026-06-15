package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceCheckInResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceGroupCountsResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.ChurchGroupAttendanceCountResponse
import com.hanmaum.dn.app.features.attendance.service.AttendanceService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.mockito.Mockito.`when`
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
        `when`(attendanceService.checkIn("kc-001"))
            .thenReturn(
                AttendanceCheckInResponse(
                    definitionPublicId = definitionId.toString(),
                    definitionTitle = "주일예배",
                    attendanceDate = date,
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
                    groups =
                        listOf(
                            ChurchGroupAttendanceCountResponse(
                                groupPublicId = groupId.toString(),
                                groupDivision = "청년부",
                                groupName = "다니엘조",
                                attendanceCount = 4,
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
}
