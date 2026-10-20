package com.hanmaum.dn.app.features.guide.api.v1

import com.hanmaum.dn.app.common.config.ApiPrefixConfig
import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.guide.repository.GuideWorshipDetailRepository
import com.hanmaum.dn.app.features.guide.repository.WorshipCountRepository
import com.hanmaum.dn.app.features.guide.service.GuideDetailsService
import com.hanmaum.dn.app.features.guide.service.GuideHomeService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@WebMvcTest(GuideHomeController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class, ApiPrefixConfig::class, GuideExceptionHandler::class)
class GuideHomeControllerTest {
    @Autowired private lateinit var mvc: MockMvc

    @MockitoBean private lateinit var service: GuideHomeService

    @MockitoBean private lateinit var members: MemberRepository

    @MockitoBean private lateinit var decoder: JwtDecoder

    @Test fun `guide readers and inherited super roles read fresh snapshots but members and anonymous cannot`() {
        val reports = mock<WorshipCountRepository>()
        whenever(reports.findAllByServiceDateLessThanEqualAndDeletedAtIsNullOrderByServiceDateDesc(any())).thenReturn(emptyList())
        val empty =
            GuideHomeService(
                reports,
                mock<GuideWorshipDetailRepository>(),
                mock<GuideDetailsService>(),
                Clock.fixed(Instant.parse("2026-10-18T08:00:00Z"), ZoneId.of("Europe/Berlin")),
            ).get()
        whenever(service.get()).thenReturn(empty)
        for (role in listOf("GUIDE_VIEWER", "GUIDE_EDITOR", "ADMIN", "PASTOR")) {
            mvc
                .perform(get("/api/v1/guide/worship-counts/home").with(jwt().authorities(SimpleGrantedAuthority("ROLE_$role"))))
                .andExpect(status().isOk)
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("EMPTY"))
                .andExpect(jsonPath("$.data.currentSunday").value("2026-10-18"))
                .andExpect(jsonPath("$.data.metrics.meanReportCount").value(0))
                .andExpect(jsonPath("$.data.timeline.length()").value(8))
        }
        mvc
            .perform(get("/api/v1/guide/worship-counts/home").with(jwt().authorities(SimpleGrantedAuthority("ROLE_MEMBER"))))
            .andExpect(status().isForbidden)
        mvc.perform(get("/api/v1/guide/worship-counts/home")).andExpect(status().isUnauthorized)
    }
}
