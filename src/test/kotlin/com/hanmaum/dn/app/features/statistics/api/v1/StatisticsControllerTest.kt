package com.hanmaum.dn.app.features.statistics.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.statistics.api.v1.dto.ChartDataDto
import com.hanmaum.dn.app.features.statistics.api.v1.dto.DashboardStatsDto
import com.hanmaum.dn.app.features.statistics.service.StatisticsPeriod
import com.hanmaum.dn.app.features.statistics.service.StatisticsService
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

@WebMvcTest(StatisticsController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class StatisticsControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var statisticsService: StatisticsService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private fun stats(period: StatisticsPeriod) =
        DashboardStatsDto(
            totalMembers = 42,
            newMembersYtd = 7,
            averageAge = 31.5,
            cityDistribution = emptyList(),
            ageDistribution = emptyList(),
            genderDistribution = listOf(ChartDataDto("자매", 22), ChartDataDto("형제", 20)),
            period = period.param,
            growthTrend = emptyList(),
            attendanceRate = emptyList(),
            serviceAttendance = emptyList(),
            divisionAttendance = emptyList(),
            trainingStages = emptyList(),
            ministryHeadcount = emptyList(),
        )

    @Test
    fun `GET dashboard wraps the stats in an api response`() {
        `when`(statisticsService.getDashboardStats(StatisticsPeriod.YEAR)).thenReturn(stats(StatisticsPeriod.YEAR))

        mockMvc
            .perform(get("/api/v1/statistics/dashboard").with(jwt().jwt { it.subject("kc-001") }))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalMembers").value(42))
            .andExpect(jsonPath("$.data.period").value("year"))
            .andExpect(jsonPath("$.data.genderDistribution[0].label").value("자매"))
    }

    @Test
    fun `GET dashboard passes the period on to the service`() {
        `when`(statisticsService.getDashboardStats(StatisticsPeriod.QUARTER)).thenReturn(stats(StatisticsPeriod.QUARTER))

        mockMvc
            .perform(
                get("/api/v1/statistics/dashboard")
                    .param("period", "quarter")
                    .with(jwt().jwt { it.subject("kc-001") }),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.period").value("quarter"))
    }

    @Test
    fun `GET dashboard requires authentication`() {
        mockMvc
            .perform(get("/api/v1/statistics/dashboard"))
            .andExpect(status().isUnauthorized)
    }
}
