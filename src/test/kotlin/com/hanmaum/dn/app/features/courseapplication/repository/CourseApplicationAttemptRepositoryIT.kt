package com.hanmaum.dn.app.features.courseapplication.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.courseapplication.domain.CourseApplicationAttempt
import com.hanmaum.dn.app.features.courseapplication.domain.CourseApplicationAttemptStatus
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.TrainingCode
import com.hanmaum.dn.app.features.training.repository.TrainingRepository
import jakarta.persistence.EntityManager
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The idempotency ledger and the training aliases, against real SQL.
 *
 * The partial unique index and the CHECK constraints of V20260915120100 cannot be declared
 * in JPA, so this is the only place they are exercised.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class CourseApplicationAttemptRepositoryIT {
    @Autowired lateinit var attemptRepository: CourseApplicationAttemptRepository

    @Autowired lateinit var trainingRepository: TrainingRepository

    @Autowired lateinit var entityManager: EntityManager

    private fun catalog(code: TrainingCode): Training =
        trainingRepository.findByCodeAndDeletedAtIsNull(code).orElseThrow {
            AssertionError("Catalog is missing $code — check the seed migration.")
        }

    private fun newMember(): Member = Member(lastName = "김", firstName = "테스트").also { entityManager.persist(it) }

    private fun attempt(
        member: Member,
        code: TrainingCode,
        externalCourseId: Int,
        status: CourseApplicationAttemptStatus = CourseApplicationAttemptStatus.PENDING,
        externalApplicationId: Long? = null,
    ) = CourseApplicationAttempt(
        member = member,
        training = catalog(code),
        externalCourseId = externalCourseId,
        externalCourseName = "큐베세 직장인/청년 반",
        externalApplicationId = externalApplicationId,
        status = status,
    )

    @Test
    fun `the seeded aliases are loaded with their trainings`() {
        assertTrue("큐베세" in catalog(TrainingCode.QT_BASIC_SEMINAR).aliases)
        assertTrue("청년부 파워제자반" in catalog(TrainingCode.YOUTH_POWER_DISCIPLESHIP).aliases)
        assertTrue("세례교육" in catalog(TrainingCode.BAPTISM_MEMBERSHIP).aliases)
    }

    @Test
    fun `active trainings are fetched with their aliases in one query`() {
        val trainings = trainingRepository.findActiveWithAliases()
        entityManager.clear()

        val qtBasic = trainings.single { it.code == TrainingCode.QT_BASIC_SEMINAR }
        // Read after clear(): a lazy collection would throw here instead of answering.
        assertTrue("큐베세" in qtBasic.aliases)
        assertTrue(trainings.none { it.code == TrainingCode.KAIROS })
    }

    @Test
    fun `a pending attempt is found again and keeps its client application id once created`() {
        val member = newMember()
        val saved = attemptRepository.saveAndFlush(attempt(member, TrainingCode.ONE_ON_ONE, 3))
        entityManager.clear()

        val found = assertNotNull(attemptRepository.findLive(member.id!!, 3))
        assertEquals(saved.clientApplicationId, found.clientApplicationId)

        found.markCreated(123L)
        attemptRepository.saveAndFlush(found)
        entityManager.clear()

        val created = assertNotNull(attemptRepository.findLive(member.id!!, 3))
        assertEquals(CourseApplicationAttemptStatus.CREATED, created.status)
        assertEquals(123L, created.externalApplicationId)
        assertEquals(
            listOf(3),
            attemptRepository.findByMember(member.id!!, setOf(CourseApplicationAttemptStatus.CREATED)).map { it.externalCourseId },
        )
    }

    @Test
    fun `a member's list holds only their own applications`() {
        val me = newMember()
        val other = newMember()
        entityManager.persist(attempt(me, TrainingCode.ONE_ON_ONE, 3, CourseApplicationAttemptStatus.CREATED, externalApplicationId = 1L))
        entityManager.persist(
            attempt(other, TrainingCode.ONE_ON_ONE, 3, CourseApplicationAttemptStatus.CREATED, externalApplicationId = 2L),
        )
        entityManager.flush()

        val mine = attemptRepository.findByMember(me.id!!, setOf(CourseApplicationAttemptStatus.CREATED))

        assertEquals(listOf(1L), mine.map { it.externalApplicationId })
    }

    @Test
    fun `a member cannot hold two live attempts for the same external course`() {
        val member = newMember()
        entityManager.persist(attempt(member, TrainingCode.QT_BASIC_SEMINAR, 106))
        entityManager.flush()

        // IDENTITY ids make Hibernate insert on persist, so the violation surfaces there.
        assertThrows<ConstraintViolationException> {
            entityManager.persist(attempt(member, TrainingCode.QT_BASIC_SEMINAR, 106))
            entityManager.flush()
        }
    }

    @Test
    fun `a cancelled attempt does not block a new one`() {
        val member = newMember()
        entityManager.persist(
            attempt(member, TrainingCode.QT_BASIC_SEMINAR, 106, CourseApplicationAttemptStatus.CANCELLED, externalApplicationId = 1L),
        )
        entityManager.persist(attempt(member, TrainingCode.QT_BASIC_SEMINAR, 106))
        entityManager.flush()

        assertEquals(CourseApplicationAttemptStatus.PENDING, attemptRepository.findLive(member.id!!, 106)?.status)
    }

    @Test
    fun `a created attempt without an external id is rejected`() {
        val invalid = attempt(newMember(), TrainingCode.ONE_ON_ONE, 3, CourseApplicationAttemptStatus.CREATED)

        assertThrows<ConstraintViolationException> {
            entityManager.persist(invalid)
            entityManager.flush()
        }
    }
}
