package com.hanmaum.dn.app.features.training.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.TrainingCode
import com.hanmaum.dn.app.features.training.domain.TrainingStatus
import com.hanmaum.dn.app.features.training.domain.UserTraining
import com.hanmaum.dn.app.features.training.service.TrainingService
import jakarta.persistence.EntityManager
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * The 양육 seat counter and the offering columns, against real SQL.
 *
 * The unit tests mock the repository, so this is the only place where the CHECK
 * constraints from V20260830140000 and the status scoping of the count are actually
 * exercised.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class TrainingOfferingIT {
    @Autowired lateinit var trainingRepository: TrainingRepository

    @Autowired lateinit var userTrainingRepository: UserTrainingRepository

    @Autowired lateinit var entityManager: EntityManager

    private fun catalog(code: TrainingCode): Training =
        trainingRepository.findByCodeAndDeletedAtIsNull(code).orElseThrow {
            AssertionError("Catalog is missing $code — check the seed migration.")
        }

    private fun newMember(): Member = Member(lastName = "김", firstName = "테스트").also { entityManager.persist(it) }

    private fun register(
        training: Training,
        status: TrainingStatus,
    ) {
        entityManager.persist(
            UserTraining(member = newMember(), training = training, status = status, appliedOn = LocalDate.of(2026, 9, 1)),
        )
    }

    @Test
    fun `the seat counter sees applications and enrolments but not historical records`() {
        val training = catalog(TrainingCode.QT_BASIC_SEMINAR)
        register(training, TrainingStatus.APPLIED)
        register(training, TrainingStatus.APPLIED)
        register(training, TrainingStatus.ENROLLED)
        // Everything an admin writes for a run that is over must stay out of the count.
        register(training, TrainingStatus.COMPLETED)
        register(training, TrainingStatus.DROPPED)
        register(training, TrainingStatus.IN_PROGRESS)
        register(training, TrainingStatus.UNKNOWN)
        entityManager.flush()

        val count =
            userTrainingRepository.countByTrainingIdAndStatusInAndDeletedAtIsNull(
                training.id!!,
                TrainingService.REGISTERED_STATUSES,
            )

        assertEquals(3, count)
    }

    @Test
    fun `a soft-deleted registration frees its seat`() {
        val training = catalog(TrainingCode.ONE_ON_ONE)
        val member = newMember()
        val row =
            UserTraining(member = member, training = training, status = TrainingStatus.APPLIED, appliedOn = LocalDate.of(2026, 9, 1))
        entityManager.persist(row)
        entityManager.flush()
        assertEquals(
            1,
            userTrainingRepository.countByTrainingIdAndStatusInAndDeletedAtIsNull(
                training.id!!,
                TrainingService.REGISTERED_STATUSES,
            ),
        )

        row.deletedAt = java.time.Instant.now()
        entityManager.flush()

        assertEquals(
            0,
            userTrainingRepository.countByTrainingIdAndStatusInAndDeletedAtIsNull(
                training.id!!,
                TrainingService.REGISTERED_STATUSES,
            ),
        )
    }

    @Test
    fun `the offering columns round-trip including the ordered target audience`() {
        val training = catalog(TrainingCode.QT_ADVANCED_SEMINAR)
        training.startDate = LocalDate.of(2026, 9, 7)
        training.durationWeeks = 4
        training.openForRegistration = true
        training.weekday = DayOfWeek.SUNDAY
        training.startTime = LocalTime.of(14, 0)
        training.durationMinutes = 90
        training.location = "본당 2층 세미나실"
        training.leaderName = "김요한 목사"
        training.capacity = 12
        training.registrationDeadline = LocalDate.of(2026, 9, 3)
        training.replaceTargetAudience(listOf("큐티를 처음 시작하는 분", "말씀 묵상이 어려운 분"))
        entityManager.flush()
        entityManager.clear()

        val reloaded = catalog(TrainingCode.QT_ADVANCED_SEMINAR)

        assertEquals(DayOfWeek.SUNDAY, reloaded.weekday)
        assertEquals(LocalTime.of(14, 0), reloaded.startTime)
        assertEquals(12, reloaded.capacity)
        assertEquals(LocalDate.of(2026, 9, 3), reloaded.registrationDeadline)
        assertEquals(listOf("큐티를 처음 시작하는 분", "말씀 묵상이 어려운 분"), reloaded.targetAudience)
    }

    @Test
    fun `a deadline after the start date is rejected by the database`() {
        val training = catalog(TrainingCode.BIBLE_PANORAMA)
        training.startDate = LocalDate.of(2026, 9, 7)
        training.registrationDeadline = LocalDate.of(2026, 9, 8)

        assertThrows<ConstraintViolationException> { entityManager.flush() }
    }

    @Test
    fun `a non-positive capacity is rejected by the database`() {
        val training = catalog(TrainingCode.MINISTRY_CLASS)
        training.capacity = 0

        assertThrows<ConstraintViolationException> { entityManager.flush() }
    }
}
