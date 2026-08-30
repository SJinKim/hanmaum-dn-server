package com.hanmaum.dn.app.features.training.service

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.TrainingCategory
import com.hanmaum.dn.app.features.training.domain.TrainingCode
import com.hanmaum.dn.app.features.training.domain.TrainingStatus
import com.hanmaum.dn.app.features.training.domain.UserTraining
import com.hanmaum.dn.app.features.training.repository.TrainingCohortRepository
import com.hanmaum.dn.app.features.training.repository.TrainingRepository
import com.hanmaum.dn.app.features.training.repository.UserTrainingRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.web.ErrorResponseException
import org.springframework.web.server.ResponseStatusException
import java.lang.reflect.Field
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TrainingServiceTest {
    @Mock private lateinit var trainingRepo: TrainingRepository

    @Mock private lateinit var cohortRepo: TrainingCohortRepository

    @Mock private lateinit var userTrainingRepo: UserTrainingRepository

    @Mock private lateinit var memberRepo: MemberRepository

    private lateinit var service: TrainingService

    private val berlinZone = ZoneId.of("Europe/Berlin")

    // Fixed clock: 2026-09-01T10:00+02:00 — a week before the sample course starts.
    private val clock = Clock.fixed(Instant.parse("2026-09-01T08:00:00Z"), berlinZone)
    private val today = LocalDate.of(2026, 9, 1)

    @BeforeEach
    fun setUp() {
        service = TrainingService(trainingRepo, cohortRepo, userTrainingRepo, memberRepo, clock)
    }

    // ─── getTrainings ─────────────────────────────────────────────────────────

    @Test
    fun `getTrainings exposes the offering fields the list renders`() {
        val training = makeTraining()
        `when`(trainingRepo.findAllByDeletedAtIsNullOrderBySortOrderAsc()).thenReturn(listOf(training))

        val result = service.getTrainings(activeOnly = false)

        assertEquals(1, result.size)
        assertEquals("Quiet Time Basic Seminar", result[0].name)
        assertEquals("매주 말씀을 묵상하는 법을 배웁니다.", result[0].description)
        assertEquals(LocalDate.of(2026, 9, 7), result[0].startDate)
        assertEquals(4, result[0].durationWeeks)
        assertTrue(result[0].openForRegistration)
    }

    @Test
    fun `getTrainings with activeOnly asks the repository for active entries only`() {
        `when`(trainingRepo.findAllByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc()).thenReturn(emptyList())

        service.getTrainings(activeOnly = true)

        verify(trainingRepo, never()).findAllByDeletedAtIsNullOrderBySortOrderAsc()
    }

    // ─── getTraining ──────────────────────────────────────────────────────────

    @Test
    fun `getTraining returns the detail block and counts only open registrations`() {
        val training = makeTraining()
        training.replaceTargetAudience(listOf("큐티를 처음 시작하는 분", "말씀 묵상이 어려운 분"))
        `when`(trainingRepo.findByPublicIdAndDeletedAtIsNull(training.publicId)).thenReturn(Optional.of(training))
        `when`(
            userTrainingRepo.countByTrainingIdAndStatusInAndDeletedAtIsNull(1L, TrainingService.REGISTERED_STATUSES),
        ).thenReturn(8)

        val result = service.getTraining(training.publicId)

        assertEquals("SUNDAY", result.weekday)
        assertEquals(LocalTime.of(14, 0), result.startTime)
        assertEquals(90, result.durationMinutes)
        assertEquals("본당 2층 세미나실", result.location)
        assertEquals("김요한 목사", result.leaderName)
        assertEquals(12, result.capacity)
        assertEquals(8, result.registeredCount)
        assertEquals(LocalDate.of(2026, 9, 3), result.registrationDeadline)
        assertEquals(listOf("큐티를 처음 시작하는 분", "말씀 묵상이 어려운 분"), result.targetAudience)
    }

    @Test
    fun `getTraining returns 404 for an unknown course`() {
        val unknownId = UUID.randomUUID()
        `when`(trainingRepo.findByPublicIdAndDeletedAtIsNull(unknownId)).thenReturn(Optional.empty())

        val ex = assertThrows<ErrorResponseException> { service.getTraining(unknownId) }

        assertEquals(404, ex.statusCode.value())
    }

    // ─── registerCurrentMember ────────────────────────────────────────────────

    @Test
    fun `registerCurrentMember stores an APPLIED row dated today`() {
        val training = makeTraining()
        val member = makeMember()
        givenMemberAndTraining(member, training)
        givenNoExistingRegistration(member, training)
        givenRegisteredCount(training, 8)
        `when`(userTrainingRepo.saveAndFlush(any<UserTraining>())).thenAnswer { it.arguments[0] as UserTraining }

        val result = service.registerCurrentMember(training.publicId, "kc-001")

        assertEquals("APPLIED", result.status)
        assertEquals(today, result.appliedOn)
        assertEquals("Quiet Time Basic Seminar", result.trainingName)
        assertEquals(12, result.capacity)
        // The caller's own seat is included, so the client can render 9 / 12 immediately.
        assertEquals(9, result.registeredCount)

        val saved = captureSaved()
        assertEquals(TrainingStatus.APPLIED, saved.status)
        assertEquals(today, saved.appliedOn)
        assertEquals(member, saved.member)
        assertEquals(training, saved.training)
        // A self-registration never picks an OT/NT variant; leaving it null is what makes
        // the duplicate check line up with uq_user_training_member_training_variant.
        assertNull(saved.variant)
    }

    @Test
    fun `registerCurrentMember rejects a course that is not taking applications`() {
        val training = makeTraining(openForRegistration = false)
        val member = makeMember()
        givenMemberAndTraining(member, training)

        val ex = assertThrows<ResponseStatusException> { service.registerCurrentMember(training.publicId, "kc-001") }

        assertEquals(400, ex.statusCode.value())
        assertEquals("현재 신청을 받고 있지 않은 과정입니다.", ex.reason)
        verify(userTrainingRepo, never()).saveAndFlush(any<UserTraining>())
    }

    @Test
    fun `registerCurrentMember rejects an application after the deadline`() {
        val training = makeTraining(registrationDeadline = today.minusDays(1))
        val member = makeMember()
        givenMemberAndTraining(member, training)

        val ex = assertThrows<ResponseStatusException> { service.registerCurrentMember(training.publicId, "kc-001") }

        assertEquals(400, ex.statusCode.value())
        assertEquals("신청 마감일이 지났습니다.", ex.reason)
        verify(userTrainingRepo, never()).saveAndFlush(any<UserTraining>())
    }

    @Test
    fun `registerCurrentMember falls back to the start date when no deadline is set`() {
        val training = makeTraining(registrationDeadline = null, startDate = today.minusDays(1))
        val member = makeMember()
        givenMemberAndTraining(member, training)

        val ex = assertThrows<ResponseStatusException> { service.registerCurrentMember(training.publicId, "kc-001") }

        assertEquals(400, ex.statusCode.value())
        assertEquals("신청 마감일이 지났습니다.", ex.reason)
    }

    @Test
    fun `registerCurrentMember accepts an application on the deadline itself`() {
        val training = makeTraining(registrationDeadline = today)
        val member = makeMember()
        givenMemberAndTraining(member, training)
        givenNoExistingRegistration(member, training)
        givenRegisteredCount(training, 0)
        `when`(userTrainingRepo.saveAndFlush(any<UserTraining>())).thenAnswer { it.arguments[0] as UserTraining }

        val result = service.registerCurrentMember(training.publicId, "kc-001")

        assertEquals("APPLIED", result.status)
    }

    @Test
    fun `registerCurrentMember rejects a second application from the same member`() {
        val training = makeTraining()
        val member = makeMember()
        givenMemberAndTraining(member, training)
        `when`(
            userTrainingRepo.findByMemberIdAndTrainingIdAndVariantAndDeletedAtIsNull(
                eq(1L),
                eq(1L),
                eq(null),
            ),
        ).thenReturn(Optional.of(UserTraining(member = member, training = training, status = TrainingStatus.APPLIED)))

        val ex = assertThrows<ResponseStatusException> { service.registerCurrentMember(training.publicId, "kc-001") }

        assertEquals(409, ex.statusCode.value())
        assertEquals("이미 신청한 과정입니다.", ex.reason)
        verify(userTrainingRepo, never()).saveAndFlush(any<UserTraining>())
    }

    @Test
    fun `registerCurrentMember reports a racing duplicate insert as a conflict`() {
        val training = makeTraining()
        val member = makeMember()
        givenMemberAndTraining(member, training)
        givenNoExistingRegistration(member, training)
        givenRegisteredCount(training, 0)
        `when`(userTrainingRepo.saveAndFlush(any<UserTraining>()))
            .thenThrow(DataIntegrityViolationException("uq_user_training_member_training_variant"))

        val ex = assertThrows<ResponseStatusException> { service.registerCurrentMember(training.publicId, "kc-001") }

        assertEquals(409, ex.statusCode.value())
        assertEquals("이미 신청한 과정입니다.", ex.reason)
    }

    @Test
    fun `registerCurrentMember rejects an application once the course is full`() {
        val training = makeTraining(capacity = 12)
        val member = makeMember()
        givenMemberAndTraining(member, training)
        givenNoExistingRegistration(member, training)
        givenRegisteredCount(training, 12)

        val ex = assertThrows<ResponseStatusException> { service.registerCurrentMember(training.publicId, "kc-001") }

        assertEquals(409, ex.statusCode.value())
        assertEquals("정원이 모두 찼습니다.", ex.reason)
        verify(userTrainingRepo, never()).saveAndFlush(any<UserTraining>())
    }

    @Test
    fun `registerCurrentMember accepts an application for an uncapped course`() {
        val training = makeTraining(capacity = null)
        val member = makeMember()
        givenMemberAndTraining(member, training)
        givenNoExistingRegistration(member, training)
        givenRegisteredCount(training, 400)
        `when`(userTrainingRepo.saveAndFlush(any<UserTraining>())).thenAnswer { it.arguments[0] as UserTraining }

        val result = service.registerCurrentMember(training.publicId, "kc-001")

        assertNull(result.capacity)
        assertEquals(401, result.registeredCount)
    }

    @Test
    fun `registerCurrentMember fails when the token has no member row`() {
        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("kc-unknown")).thenReturn(null)

        assertThrows<EntityNotFoundException> { service.registerCurrentMember(UUID.randomUUID(), "kc-unknown") }

        verify(userTrainingRepo, never()).saveAndFlush(any<UserTraining>())
    }

    @Test
    fun `REGISTERED_STATUSES excludes historical participation`() {
        assertEquals(setOf(TrainingStatus.APPLIED, TrainingStatus.ENROLLED), TrainingService.REGISTERED_STATUSES)
        assertFalse(TrainingStatus.COMPLETED in TrainingService.REGISTERED_STATUSES)
        assertFalse(TrainingStatus.UNKNOWN in TrainingService.REGISTERED_STATUSES)
        assertFalse(TrainingStatus.IN_PROGRESS in TrainingService.REGISTERED_STATUSES)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun givenMemberAndTraining(
        member: Member,
        training: Training,
    ) {
        `when`(memberRepo.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member)
        `when`(trainingRepo.findByPublicIdAndDeletedAtIsNull(training.publicId)).thenReturn(Optional.of(training))
    }

    private fun givenNoExistingRegistration(
        member: Member,
        training: Training,
    ) {
        `when`(
            userTrainingRepo.findByMemberIdAndTrainingIdAndVariantAndDeletedAtIsNull(
                eq(member.id!!),
                eq(training.id!!),
                eq(null),
            ),
        ).thenReturn(Optional.empty())
    }

    private fun givenRegisteredCount(
        training: Training,
        count: Int,
    ) {
        `when`(
            userTrainingRepo.countByTrainingIdAndStatusInAndDeletedAtIsNull(
                training.id!!,
                TrainingService.REGISTERED_STATUSES,
            ),
        ).thenReturn(count)
    }

    private fun captureSaved(): UserTraining {
        val captor = org.mockito.kotlin.argumentCaptor<UserTraining>()
        verify(userTrainingRepo).saveAndFlush(captor.capture())
        return captor.firstValue
    }

    private fun makeTraining(
        id: Long = 1L,
        openForRegistration: Boolean = true,
        capacity: Int? = 12,
        startDate: LocalDate? = LocalDate.of(2026, 9, 7),
        registrationDeadline: LocalDate? = LocalDate.of(2026, 9, 3),
    ): Training {
        val training =
            Training(
                code = TrainingCode.QT_BASIC_SEMINAR,
                name = "Quiet Time Basic Seminar",
                sortOrder = 20,
                nameKo = "큐티베이직세미나",
                category = TrainingCategory.FOUNDATION,
                description = "매주 말씀을 묵상하는 법을 배웁니다.",
                startDate = startDate,
                durationWeeks = 4,
                openForRegistration = openForRegistration,
                weekday = DayOfWeek.SUNDAY,
                startTime = LocalTime.of(14, 0),
                durationMinutes = 90,
                location = "본당 2층 세미나실",
                leaderName = "김요한 목사",
                capacity = capacity,
                registrationDeadline = registrationDeadline,
            )
        setId(training, id)
        return training
    }

    private fun makeMember(
        id: Long = 1L,
        keycloakId: String = "kc-001",
    ): Member {
        val member = Member(lastName = "김", firstName = "철수")
        setId(member, id)
        val field: Field = Member::class.java.getDeclaredField("keycloakId")
        field.isAccessible = true
        field.set(member, keycloakId)
        return member
    }

    private fun setId(
        entity: Any,
        id: Long,
    ) {
        val field: Field = entity.javaClass.superclass.getDeclaredField("id")
        field.isAccessible = true
        field.set(entity, id)
    }
}
