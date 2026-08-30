package com.hanmaum.dn.app.features.training.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDetailDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingRegistrationDto
import com.hanmaum.dn.app.features.training.service.TrainingService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test

@WebMvcTest(TrainingController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class TrainingControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var trainingService: TrainingService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val trainingId = UUID.randomUUID()

    private fun sampleDto() =
        TrainingDto(
            publicId = trainingId.toString(),
            name = "Quiet Time Basic Seminar",
            sortOrder = 20,
            description = "매주 말씀을 묵상하는 법을 배웁니다.",
            startDate = LocalDate.of(2026, 9, 7),
            durationWeeks = 4,
            openForRegistration = true,
        )

    private fun sampleDetailDto() =
        TrainingDetailDto(
            publicId = trainingId.toString(),
            name = "Quiet Time Basic Seminar",
            nameKo = "큐티베이직세미나",
            category = "FOUNDATION",
            sortOrder = 20,
            description = "매주 말씀을 묵상하는 법을 배웁니다.",
            startDate = LocalDate.of(2026, 9, 7),
            durationWeeks = 4,
            openForRegistration = true,
            weekday = "SUNDAY",
            startTime = LocalTime.of(14, 0),
            durationMinutes = 90,
            location = "본당 2층 세미나실",
            leaderName = "김요한 목사",
            capacity = 12,
            registeredCount = 8,
            registrationDeadline = LocalDate.of(2026, 9, 3),
            targetAudience = listOf("큐티를 처음 시작하는 분"),
        )

    // ─── GET /trainings ───────────────────────────────────────────────────────

    @Test
    fun `GET trainings returns the list for a plain member`() {
        `when`(trainingService.getTrainings(false)).thenReturn(listOf(sampleDto()))

        mockMvc
            .perform(get("/api/v1/trainings").with(jwt().authorities(SimpleGrantedAuthority("ROLE_MEMBER"))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].name").value("Quiet Time Basic Seminar"))
            .andExpect(jsonPath("$.data[0].startDate").value("2026-09-07"))
            .andExpect(jsonPath("$.data[0].durationWeeks").value(4))
            .andExpect(jsonPath("$.data[0].openForRegistration").value(true))
    }

    @Test
    fun `GET trainings defaults to the full catalog the admin form expects`() {
        `when`(trainingService.getTrainings(false)).thenReturn(emptyList())

        mockMvc
            .perform(get("/api/v1/trainings").with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk)

        verify(trainingService).getTrainings(false)
    }

    @Test
    fun `GET trainings passes activeOnly through`() {
        `when`(trainingService.getTrainings(true)).thenReturn(emptyList())

        mockMvc
            .perform(
                get("/api/v1/trainings")
                    .param("activeOnly", "true")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_MEMBER"))),
            ).andExpect(status().isOk)

        verify(trainingService).getTrainings(true)
    }

    @Test
    fun `GET trainings returns 401 without a token`() {
        mockMvc.perform(get("/api/v1/trainings")).andExpect(status().isUnauthorized)
    }

    // ─── GET /trainings/{publicId} ────────────────────────────────────────────

    @Test
    fun `GET training detail returns the schedule block and seat counter`() {
        `when`(trainingService.getTraining(trainingId)).thenReturn(sampleDetailDto())

        mockMvc
            .perform(get("/api/v1/trainings/$trainingId").with(jwt().authorities(SimpleGrantedAuthority("ROLE_MEMBER"))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.weekday").value("SUNDAY"))
            .andExpect(jsonPath("$.data.startTime").value("14:00"))
            .andExpect(jsonPath("$.data.durationMinutes").value(90))
            .andExpect(jsonPath("$.data.location").value("본당 2층 세미나실"))
            .andExpect(jsonPath("$.data.leaderName").value("김요한 목사"))
            .andExpect(jsonPath("$.data.capacity").value(12))
            .andExpect(jsonPath("$.data.registeredCount").value(8))
            .andExpect(jsonPath("$.data.registrationDeadline").value("2026-09-03"))
            .andExpect(jsonPath("$.data.targetAudience[0]").value("큐티를 처음 시작하는 분"))
    }

    @Test
    fun `GET training detail returns 401 without a token`() {
        mockMvc.perform(get("/api/v1/trainings/$trainingId")).andExpect(status().isUnauthorized)
    }

    // ─── POST /trainings/{publicId}/registrations ─────────────────────────────

    @Test
    fun `POST registrations signs the caller up and returns 201`() {
        `when`(trainingService.registerCurrentMember(eq(trainingId), eq("kc-001")))
            .thenReturn(
                TrainingRegistrationDto(
                    trainingPublicId = trainingId.toString(),
                    trainingName = "Quiet Time Basic Seminar",
                    status = "APPLIED",
                    appliedOn = LocalDate.of(2026, 9, 1),
                    registeredCount = 9,
                    capacity = 12,
                ),
            )

        mockMvc
            .perform(
                post("/api/v1/trainings/$trainingId/registrations")
                    .with(
                        jwt()
                            .jwt { it.subject("kc-001") }
                            .authorities(SimpleGrantedAuthority("ROLE_MEMBER")),
                    ),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.status").value("APPLIED"))
            .andExpect(jsonPath("$.data.appliedOn").value("2026-09-01"))
            .andExpect(jsonPath("$.data.registeredCount").value(9))
    }

    @Test
    fun `POST registrations surfaces a full course as 409`() {
        `when`(trainingService.registerCurrentMember(eq(trainingId), eq("kc-001")))
            .thenThrow(ResponseStatusException(HttpStatus.CONFLICT, "정원이 모두 찼습니다."))

        mockMvc
            .perform(
                post("/api/v1/trainings/$trainingId/registrations")
                    .with(
                        jwt()
                            .jwt { it.subject("kc-001") }
                            .authorities(SimpleGrantedAuthority("ROLE_MEMBER")),
                    ),
            ).andExpect(status().isConflict)
    }

    @Test
    fun `POST registrations returns 401 without a token`() {
        mockMvc
            .perform(post("/api/v1/trainings/$trainingId/registrations"))
            .andExpect(status().isUnauthorized)
    }

    // ─── Admin-only endpoints stay admin-only ─────────────────────────────────

    @Test
    fun `GET catalog returns 403 for a plain member`() {
        mockMvc
            .perform(get("/api/v1/trainings/catalog").with(jwt().authorities(SimpleGrantedAuthority("ROLE_MEMBER"))))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET cohorts returns 403 for a plain member`() {
        mockMvc
            .perform(
                get("/api/v1/trainings/$trainingId/cohorts")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_MEMBER"))),
            ).andExpect(status().isForbidden)
    }
}
