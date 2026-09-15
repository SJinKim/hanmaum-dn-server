package com.hanmaum.dn.app.features.courseapplication.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.courseapplication.service.CourseApplicationService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.training.api.v1.dto.MyTrainingApplicationDto
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID
import kotlin.test.Test

@WebMvcTest(MyTrainingApplicationController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MyTrainingApplicationControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var courseApplicationService: CourseApplicationService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `GET me trainings lists the caller's applications with date and status`() {
        `when`(courseApplicationService.myTrainingApplications("kc-001")).thenReturn(
            listOf(
                MyTrainingApplicationDto(
                    trainingPublicId = UUID.randomUUID().toString(),
                    trainingName = "Quiet Time Basic Seminar",
                    trainingNameKo = "큐티베이직세미나",
                    externalCourseId = 106,
                    courseName = "큐베세 직장인/청년 반",
                    appliedAt = Instant.parse("2026-09-14T10:00:00Z"),
                    status = "ENROLLED",
                ),
            ),
        )

        mockMvc
            .perform(
                get("/api/v1/me/trainings")
                    .with(jwt().jwt { it.subject("kc-001") }.authorities(SimpleGrantedAuthority("ROLE_MEMBER"))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].trainingNameKo").value("큐티베이직세미나"))
            .andExpect(jsonPath("$.data[0].courseName").value("큐베세 직장인/청년 반"))
            .andExpect(jsonPath("$.data[0].appliedAt").value("2026-09-14T10:00:00Z"))
            .andExpect(jsonPath("$.data[0].status").value("ENROLLED"))
    }

    @Test
    fun `GET me trainings returns 401 without a token`() {
        mockMvc.perform(get("/api/v1/me/trainings")).andExpect(status().isUnauthorized)
    }
}
