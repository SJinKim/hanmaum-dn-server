package com.hanmaum.dn.app.features.training.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.TrainingCode
import com.hanmaum.dn.app.features.training.domain.TrainingStatus
import com.hanmaum.dn.app.features.training.domain.TrainingVariant
import com.hanmaum.dn.app.features.training.domain.UserTraining
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

/**
 * 성경개관 is taken once for the Old Testament and once for the New. The original
 * UNIQUE (user_id, training_id) made that impossible; these tests pin the replacement.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class UserTrainingVariantIT {
    @Autowired lateinit var trainingRepository: TrainingRepository

    @Autowired lateinit var entityManager: EntityManager

    private fun catalog(code: TrainingCode): Training =
        trainingRepository.findByCodeAndDeletedAtIsNull(code).orElseThrow {
            AssertionError("Catalog is missing $code — check the seed migration.")
        }

    private fun newMember(): Member = Member(lastName = "김", firstName = "테스트").also { entityManager.persist(it) }

    @Test
    fun `one member may hold both testament variants of bible overview`() {
        val member = newMember()
        val bibleOverview = catalog(TrainingCode.BIBLE_OVERVIEW)

        entityManager.persist(
            UserTraining(
                member = member,
                training = bibleOverview,
                status = TrainingStatus.COMPLETED,
                variant = TrainingVariant.OLD_TESTAMENT,
            ),
        )
        entityManager.persist(
            UserTraining(
                member = member,
                training = bibleOverview,
                status = TrainingStatus.COMPLETED,
                variant = TrainingVariant.NEW_TESTAMENT,
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val stored =
            entityManager
                .createQuery(
                    "SELECT ut FROM UserTraining ut WHERE ut.member.id = :id",
                    UserTraining::class.java,
                ).setParameter("id", member.id!!)
                .resultList

        assertEquals(2, stored.size, "Both testament variants must coexist")
        assertEquals(
            setOf(TrainingVariant.OLD_TESTAMENT, TrainingVariant.NEW_TESTAMENT),
            stored.mapNotNull { it.variant }.toSet(),
        )
    }

    @Test
    fun `the same variant twice is rejected`() {
        val member = newMember()
        val bibleOverview = catalog(TrainingCode.BIBLE_OVERVIEW)

        entityManager.persist(
            UserTraining(
                member = member,
                training = bibleOverview,
                status = TrainingStatus.COMPLETED,
                variant = TrainingVariant.OLD_TESTAMENT,
            ),
        )
        entityManager.flush()

        val thrown =
            assertThrows<ConstraintViolationException> {
                entityManager.persist(
                    UserTraining(
                        member = member,
                        training = bibleOverview,
                        status = TrainingStatus.COMPLETED,
                        variant = TrainingVariant.OLD_TESTAMENT,
                    ),
                )
                entityManager.flush()
            }

        assertEquals("uq_user_training_member_training_variant", thrown.constraintName)
    }

    @Test
    fun `a course without a variant still allows only one row per member`() {
        val member = newMember()
        val qtBasic = catalog(TrainingCode.QT_BASIC_SEMINAR)

        entityManager.persist(
            UserTraining(member = member, training = qtBasic, status = TrainingStatus.COMPLETED),
        )
        entityManager.flush()

        // COALESCE(variant, '') is what stops two NULL-variant rows from slipping past
        // the index the way NULLs normally do in a unique constraint.
        val thrown =
            assertThrows<ConstraintViolationException> {
                entityManager.persist(
                    UserTraining(member = member, training = qtBasic, status = TrainingStatus.IN_PROGRESS),
                )
                entityManager.flush()
            }

        assertEquals("uq_user_training_member_training_variant", thrown.constraintName)
    }
}
