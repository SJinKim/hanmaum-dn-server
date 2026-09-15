package com.hanmaum.dn.app.features.training.api.v1

import com.hanmaum.dn.app.common.api.ApiErrorCode
import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.courseapplication.service.CourseApplicationException
import com.hanmaum.dn.app.features.courseapplication.service.CourseApplicationService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.training.api.v1.dto.ApplicantPrefillDto
import com.hanmaum.dn.app.features.training.api.v1.dto.MyTrainingApplicationDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingApplicationRequest
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCourseDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDetailDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingRegistrationDto
import com.hanmaum.dn.app.features.training.service.TrainingService
import org.hamcrest.Matchers.startsWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

@WebMvcTest(TrainingController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class TrainingControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var trainingService: TrainingService

    @MockitoBean private lateinit var courseApplicationService: CourseApplicationService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val trainingId = UUID.randomUUID()

    private val member = jwt().jwt { it.subject("kc-001") }.authorities(SimpleGrantedAuthority("ROLE_MEMBER"))

    private fun sampleDto() =
        TrainingDto(
            publicId = trainingId.toString(),
            name = "One-to-One Discipleship Training",
            sortOrder = 40,
            description = null,
            startDate = null,
            durationWeeks = null,
            openForRegistration = true,
            registrationStartsAt = null,
            registrationEndsAt = OffsetDateTime.parse("2099-03-12T23:59:59+01:00"),
            isAlwaysOpen = true,
            myApplication = sampleApplication(),
        )

    private fun sampleApplication() =
        MyTrainingApplicationDto(
            trainingPublicId = trainingId.toString(),
            trainingName = "One-to-One Discipleship Training",
            trainingNameKo = "일대일제자양육",
            externalCourseId = 3,
            courseName = "일대일 제자양육",
            appliedAt = Instant.parse("2026-09-14T10:00:00Z"),
            status = "APPLIED",
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
            capacity = null,
            registeredCount = 0,
            registrationDeadline = null,
            targetAudience = listOf("큐티를 처음 시작하는 분"),
            courses =
                listOf(
                    TrainingCourseDto(
                        externalCourseId = 105,
                        name = "큐베세 여자반",
                        dateText = "3월 2일 - 3월 23일 매주 월요일 오전 9시30분 비전홀",
                        description = null,
                        secondaryText = null,
                        registrationStartsAt = null,
                        registrationEndsAt = OffsetDateTime.parse("2026-09-30T23:59:59+02:00"),
                        isAlwaysOpen = false,
                        isEligible = false,
                        formFields = emptyList(),
                    ),
                ),
            applicantPrefill =
                ApplicantPrefillDto(
                    name = "김철수",
                    birthDate = LocalDate.of(1995, 5, 1),
                    email = "a@example.com",
                    phone = null,
                    gender = "M",
                    residence = null,
                ),
        )

    // ─── GET /trainings ───────────────────────────────────────────────────────

    @Test
    fun `GET trainings defaults to the stored catalog the admin form expects`() {
        `when`(trainingService.getTrainings(false)).thenReturn(emptyList())

        mockMvc
            .perform(get("/api/v1/trainings").with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk)

        verify(trainingService).getTrainings(false)
        verify(courseApplicationService, never()).listTrainings(any())
    }

    @Test
    fun `GET trainings with activeOnly is the 양육 list for the caller`() {
        `when`(courseApplicationService.listTrainings("kc-001")).thenReturn(listOf(sampleDto()))

        mockMvc
            .perform(get("/api/v1/trainings").param("activeOnly", "true").with(member))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].openForRegistration").value(true))
            .andExpect(jsonPath("$.data[0].isAlwaysOpen").value(true))
            .andExpect(jsonPath("$.data[0].registrationEndsAt").value(startsWith("2099-03-12T")))
            .andExpect(jsonPath("$.data[0].myApplication.courseName").value("일대일 제자양육"))
            .andExpect(jsonPath("$.data[0].myApplication.status").value("APPLIED"))
    }

    @Test
    fun `GET trainings answers 503 with a code when the application API is down`() {
        `when`(courseApplicationService.listTrainings("kc-001")).thenThrow(
            CourseApplicationException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.COURSE_APPLICATION_UNAVAILABLE, "준비중입니다."),
        )

        mockMvc
            .perform(get("/api/v1/trainings").param("activeOnly", "true").with(member))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("COURSE_APPLICATION_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("준비중입니다."))
    }

    @Test
    fun `GET trainings returns 401 without a token`() {
        mockMvc.perform(get("/api/v1/trainings")).andExpect(status().isUnauthorized)
    }

    // ─── GET /trainings/{publicId} ────────────────────────────────────────────

    @Test
    fun `GET training detail returns the schedule, the open courses and the prefill`() {
        `when`(courseApplicationService.getTrainingDetail(trainingId, "kc-001")).thenReturn(sampleDetailDto())

        mockMvc
            .perform(get("/api/v1/trainings/$trainingId").with(member))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.startTime").value("14:00"))
            .andExpect(jsonPath("$.data.targetAudience[0]").value("큐티를 처음 시작하는 분"))
            .andExpect(jsonPath("$.data.courses[0].externalCourseId").value(105))
            .andExpect(jsonPath("$.data.courses[0].isEligible").value(false))
            .andExpect(jsonPath("$.data.applicantPrefill.name").value("김철수"))
    }

    @Test
    fun `GET training detail returns 401 without a token`() {
        mockMvc.perform(get("/api/v1/trainings/$trainingId")).andExpect(status().isUnauthorized)
    }

    // ─── POST /trainings/{publicId}/registrations ─────────────────────────────

    private fun registration() =
        TrainingRegistrationDto(
            trainingPublicId = trainingId.toString(),
            trainingName = "Quiet Time Basic Seminar",
            status = "APPLIED",
            appliedOn = LocalDate.of(2026, 9, 14),
            registeredCount = 1,
            capacity = null,
            externalCourseId = 106,
            courseName = "큐베세 직장인/청년 반",
        )

    @Test
    fun `POST registrations applies the caller to the chosen course and returns 201`() {
        `when`(courseApplicationService.apply(eq(trainingId), eq("kc-001"), any())).thenReturn(registration())

        mockMvc
            .perform(
                post("/api/v1/trainings/$trainingId/registrations")
                    .with(member)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"externalCourseId":106,"phone":"+49 170 0000000","history":"큐베세 / 2017년 5월"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.status").value("APPLIED"))
            .andExpect(jsonPath("$.data.externalCourseId").value(106))
            .andExpect(jsonPath("$.data.courseName").value("큐베세 직장인/청년 반"))

        val captor = argumentCaptor<TrainingApplicationRequest>()
        verify(courseApplicationService).apply(eq(trainingId), eq("kc-001"), captor.capture())
        assertEquals(106, captor.firstValue.externalCourseId)
        assertEquals("+49 170 0000000", captor.firstValue.phone)
        assertEquals("큐베세 / 2017년 5월", captor.firstValue.history)
    }

    @Test
    fun `POST registrations without a course is a 400`() {
        mockMvc
            .perform(
                post("/api/v1/trainings/$trainingId/registrations")
                    .with(member)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"phone":"+49 170 0000000"}"""),
            ).andExpect(status().isBadRequest)

        verify(courseApplicationService, never()).apply(any(), any(), any())
    }

    @Test
    fun `POST registrations with a malformed email is a 400`() {
        mockMvc
            .perform(
                post("/api/v1/trainings/$trainingId/registrations")
                    .with(member)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"externalCourseId":106,"email":"not-an-email"}"""),
            ).andExpect(status().isBadRequest)

        verify(courseApplicationService, never()).apply(any(), any(), any())
    }

    @Test
    fun `POST registrations surfaces a full course as 409 with its code`() {
        `when`(courseApplicationService.apply(eq(trainingId), eq("kc-001"), any())).thenThrow(
            CourseApplicationException(HttpStatus.CONFLICT, ApiErrorCode.COURSE_APPLICATION_FULL, "정원이 마감되었습니다."),
        )

        mockMvc
            .perform(
                post("/api/v1/trainings/$trainingId/registrations")
                    .with(member)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"externalCourseId":106}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("COURSE_APPLICATION_FULL"))
    }

    @Test
    fun `POST registrations carries field errors from a rejected application`() {
        `when`(courseApplicationService.apply(eq(trainingId), eq("kc-001"), any())).thenThrow(
            CourseApplicationException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ApiErrorCode.COURSE_APPLICATION_INVALID,
                "입력값을 확인해주세요.",
                fieldErrors = mapOf("email" to "올바른 이메일 주소여야 합니다."),
            ),
        )

        mockMvc
            .perform(
                post("/api/v1/trainings/$trainingId/registrations")
                    .with(member)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"externalCourseId":106}"""),
            ).andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.code").value("COURSE_APPLICATION_INVALID"))
            .andExpect(jsonPath("$.fieldErrors.email").value("올바른 이메일 주소여야 합니다."))
    }

    @Test
    fun `POST registrations returns 401 without a token`() {
        mockMvc
            .perform(
                post("/api/v1/trainings/$trainingId/registrations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"externalCourseId":106}"""),
            ).andExpect(status().isUnauthorized)
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
