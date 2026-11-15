package com.hanmaum.dn.app.features.dishwashing.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.dishwashing.api.v1.dto.DishwashingDayDto
import com.hanmaum.dn.app.features.dishwashing.service.DishwashingService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.mockito.Mockito.verifyNoInteractions
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import kotlin.test.Test

/** #286: only an admin may write the 설거지 schedule. */
@WebMvcTest(DishwashingController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class DishwashingControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var service: DishwashingService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val body = """{"date":"2026-10-11","groupIds":[1,2]}"""

    private fun member() = jwt().jwt { it.subject("kc-002") }

    private fun admin() = jwt().jwt { it.subject("kc-001") }.authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    @Test
    fun `POST is forbidden for a plain member`() {
        mockMvc
            .perform(post("/api/v1/dishwashing").with(member()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `GET returns wrapped empty schedule`() {
        mockMvc
            .perform(get("/api/v1/dishwashing").with(member()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isEmpty)
    }

    @Test
    fun `GET returns wrapped schedule data`() {
        org.mockito.Mockito.`when`(service.getUpcomingSchedule()).thenReturn(
            listOf(DishwashingDayDto(LocalDate.parse("2026-10-11"), listOf("청년 1순"), "준비")),
        )

        mockMvc
            .perform(get("/api/v1/dishwashing").with(member()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].groupNames[0]").value("청년 1순"))
    }

    @Test
    fun `POST is allowed for an admin`() {
        mockMvc
            .perform(post("/api/v1/dishwashing").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated)
    }

    @Test
    fun `DELETE is forbidden for a plain member`() {
        mockMvc
            .perform(delete("/api/v1/dishwashing").param("date", "2026-10-11").with(member()))
            .andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `DELETE is allowed for an admin`() {
        mockMvc
            .perform(delete("/api/v1/dishwashing").param("date", "2026-10-11").with(admin()))
            .andExpect(status().isNoContent)
    }
}
