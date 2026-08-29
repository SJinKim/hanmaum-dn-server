package com.hanmaum.dn.app.features.training.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.training.domain.CohortSeries
import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.TrainingCode
import com.hanmaum.dn.app.features.training.domain.TrainingCohort
import jakarta.persistence.EntityManager
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class TrainingCohortRepositoryIT {
    @Autowired lateinit var repository: TrainingCohortRepository

    @Autowired lateinit var trainingRepository: TrainingRepository

    @Autowired lateinit var entityManager: EntityManager

    /** Uses the catalog seeded by V20260810120010, not ad-hoc rows. */
    private fun catalog(code: TrainingCode): Training =
        trainingRepository.findByCodeAndDeletedAtIsNull(code).orElseThrow {
            AssertionError("Catalog is missing $code — check the seed migration.")
        }

    @Test
    fun `same series and ordinal under one training is rejected even with different labels`() {
        val training = catalog(TrainingCode.YOUTH_POWER_DISCIPLESHIP)
        entityManager.persist(
            TrainingCohort(training = training, ordinal = 3, series = CohortSeries.POWER, label = "파워3기"),
        )
        entityManager.flush()

        // Same intake, spelled the way the detailed sheet writes it. Keying on the label
        // would let this through and split one cohort across two rows.
        val duplicate =
            TrainingCohort(
                training = training,
                ordinal = 3,
                series = CohortSeries.POWER,
                label = "청년파워제자반 3기 (2020)",
                cohortYear = 2020,
            )

        // persist() issues the INSERT immediately under IDENTITY generation, so the
        // violation can surface here rather than at flush(); both are inside the block.
        val thrown =
            assertThrows<ConstraintViolationException> {
                entityManager.persist(duplicate)
                entityManager.flush()
            }

        assertEquals(
            "uq_training_cohort_ordinal",
            thrown.constraintName,
            "The intake must be rejected by the ordinal key, not by some unrelated constraint",
        )
    }

    @Test
    fun `same ordinal in a different series is accepted`() {
        val training = catalog(TrainingCode.YOUTH_POWER_DISCIPLESHIP)
        entityManager.persist(
            TrainingCohort(training = training, ordinal = 1, series = CohortSeries.LEGACY, label = "1기"),
        )
        entityManager.persist(
            TrainingCohort(training = training, ordinal = 1, series = CohortSeries.POWER, label = "파워1기"),
        )
        entityManager.flush()
        entityManager.clear()

        val legacy =
            repository.findByTrainingIdAndSeriesAndOrdinalAndDeletedAtIsNull(
                training.id!!,
                CohortSeries.LEGACY,
                1,
            )
        val power =
            repository.findByTrainingIdAndSeriesAndOrdinalAndDeletedAtIsNull(
                training.id!!,
                CohortSeries.POWER,
                1,
            )

        assertNotNull(legacy.orElse(null), "LEGACY 1기 must exist alongside POWER 1기")
        assertNotNull(power.orElse(null), "POWER 1기 must exist alongside LEGACY 1기")
        assertEquals("1기", legacy.get().label)
        assertEquals("파워1기", power.get().label)
    }

    @Test
    fun `same series and ordinal under different trainings is accepted`() {
        val first = catalog(TrainingCode.YOUTH_POWER_DISCIPLESHIP)
        val second = catalog(TrainingCode.ONE_ON_ONE_SCHOOL)
        entityManager.persist(TrainingCohort(training = first, ordinal = 2, series = CohortSeries.POWER))
        entityManager.persist(TrainingCohort(training = second, ordinal = 2, series = CohortSeries.POWER))
        entityManager.flush()
        entityManager.clear()

        assertEquals(
            1,
            repository.findAllByTrainingIdAndDeletedAtIsNullOrderBySeriesAscOrdinalAsc(first.id!!).size,
        )
        assertEquals(
            1,
            repository.findAllByTrainingIdAndDeletedAtIsNullOrderBySeriesAscOrdinalAsc(second.id!!).size,
        )
    }
}
