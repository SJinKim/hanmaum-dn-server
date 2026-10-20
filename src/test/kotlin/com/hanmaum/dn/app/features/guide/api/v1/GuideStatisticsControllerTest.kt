package com.hanmaum.dn.app.features.guide.api.v1

import com.hanmaum.dn.app.common.config.ApiPrefixConfig
import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.guide.repository.WorshipCountRepository
import com.hanmaum.dn.app.features.guide.service.GuideException
import com.hanmaum.dn.app.features.guide.service.GuideStatisticsService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@WebMvcTest(GuideStatisticsController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class, ApiPrefixConfig::class, GuideExceptionHandler::class)
class GuideStatisticsControllerTest {
    @Autowired private lateinit var mvc: MockMvc

    @MockitoBean private lateinit var service: GuideStatisticsService

    @MockitoBean private lateinit var members: MemberRepository

    @MockitoBean private lateinit var decoder: JwtDecoder
    private val url = "/api/v1/guide/worship-counts/statistics"
    private val date = LocalDate.parse("2026-10-18")

    private fun actor(role: String) = jwt().authorities(SimpleGrantedAuthority("ROLE_$role"))

    @Test fun `Guide read roles and super roles can read while members and anonymous cannot`() {
        val repository = mock<WorshipCountRepository>()
        whenever(repository.findAllByServiceDateBetweenAndDeletedAtIsNullOrderByServiceDateAsc(any(), any())).thenReturn(emptyList())
        val response =
            GuideStatisticsService(
                repository,
                Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneId.of("Europe/Berlin")),
            ).statistics(2026, date)
        whenever(service.statistics(2026, date)).thenReturn(response)
        for (role in listOf("GUIDE_VIEWER", "GUIDE_EDITOR", "ADMIN", "PASTOR")) {
            mvc
                .perform(get(url).param("year", "2026").param("asOf", date.toString()).with(actor(role)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.year").value(2026))
                .andExpect(jsonPath("$.data.current.completeCount").value(0))
                .andExpect(jsonPath("$.data.current.metrics[3].average").value(nullValue()))
                .andExpect(jsonPath("$.data.current.metrics[3].averageReason").value("NO_COMPLETE_REPORTS"))
        }
        mvc.perform(get(url).param("year", "2026").with(actor("MEMBER"))).andExpect(status().isForbidden)
        mvc.perform(get(url).param("year", "2026")).andExpect(status().isUnauthorized)
    }

    @Test fun `invalid parameter shapes and domain ranges return 400`() {
        mvc.perform(get(url).with(actor("GUIDE_VIEWER"))).andExpect(status().isBadRequest)
        mvc.perform(get(url).param("year", "abc").with(actor("GUIDE_VIEWER"))).andExpect(status().isBadRequest)
        mvc.perform(get(url).param("year", "2026").param("asOf", "not-a-date").with(actor("GUIDE_VIEWER"))).andExpect(status().isBadRequest)
        whenever(service.statistics(1, null)).thenThrow(GuideException(HttpStatus.BAD_REQUEST, "Invalid year."))
        mvc
            .perform(get(url).param("year", "1").with(actor("GUIDE_VIEWER")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Invalid year."))
    }
}
