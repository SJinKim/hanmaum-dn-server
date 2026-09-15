package com.hanmaum.dn.app.features.training.service

import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.TrainingCategory
import com.hanmaum.dn.app.features.training.domain.TrainingCode
import com.hanmaum.dn.app.features.training.domain.TrainingStatus
import com.hanmaum.dn.app.features.training.repository.TrainingCohortRepository
import com.hanmaum.dn.app.features.training.repository.TrainingRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import org.springframework.web.ErrorResponseException
import java.lang.reflect.Field
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

/**
 * The stored catalog. The 양육 list, detail and application are covered by
 * CourseApplicationServiceTest, since their registration state comes from the external API.
 */
@ExtendWith(MockitoExtension::class)
class TrainingServiceTest {
    @Mock private lateinit var trainingRepo: TrainingRepository

    @Mock private lateinit var cohortRepo: TrainingCohortRepository

    private lateinit var service: TrainingService

    @BeforeEach
    fun setUp() {
        service = TrainingService(trainingRepo, cohortRepo)
    }

    // ─── getTrainings ─────────────────────────────────────────────────────────

    @Test
    fun `getTrainings exposes the stored offering fields the admin catalog renders`() {
        val training = makeTraining()
        `when`(trainingRepo.findAllByDeletedAtIsNullOrderBySortOrderAsc()).thenReturn(listOf(training))

        val result = service.getTrainings(activeOnly = false)

        assertEquals(1, result.size)
        assertEquals("Quiet Time Basic Seminar", result[0].name)
        assertEquals("매주 말씀을 묵상하는 법을 배웁니다.", result[0].description)
        assertEquals(LocalDate.of(2026, 9, 7), result[0].startDate)
        assertEquals(4, result[0].durationWeeks)
        assertTrue(result[0].openForRegistration)
        // Nothing from the external API on the stored catalog.
        assertFalse(result[0].isAlwaysOpen)
        assertEquals(null, result[0].myApplication)
    }

    @Test
    fun `getTrainings with activeOnly asks the repository for active entries only`() {
        `when`(trainingRepo.findAllByIsActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc()).thenReturn(emptyList())

        service.getTrainings(activeOnly = true)

        verify(trainingRepo, never()).findAllByDeletedAtIsNullOrderBySortOrderAsc()
    }

    // ─── getCohorts ───────────────────────────────────────────────────────────

    @Test
    fun `getCohorts returns 404 for an unknown course`() {
        val unknownId = UUID.randomUUID()
        `when`(trainingRepo.findByPublicIdAndDeletedAtIsNull(unknownId)).thenReturn(Optional.empty())

        val ex = assertThrows<ErrorResponseException> { service.getCohorts(unknownId) }

        assertEquals(404, ex.statusCode.value())
    }

    @Test
    fun `REGISTERED_STATUSES excludes historical participation`() {
        assertEquals(setOf(TrainingStatus.APPLIED, TrainingStatus.ENROLLED), TrainingService.REGISTERED_STATUSES)
        assertFalse(TrainingStatus.COMPLETED in TrainingService.REGISTERED_STATUSES)
        assertFalse(TrainingStatus.UNKNOWN in TrainingService.REGISTERED_STATUSES)
        assertFalse(TrainingStatus.IN_PROGRESS in TrainingService.REGISTERED_STATUSES)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeTraining(id: Long = 1L): Training {
        val training =
            Training(
                code = TrainingCode.QT_BASIC_SEMINAR,
                name = "Quiet Time Basic Seminar",
                sortOrder = 20,
                nameKo = "큐티베이직세미나",
                category = TrainingCategory.FOUNDATION,
                description = "매주 말씀을 묵상하는 법을 배웁니다.",
                startDate = LocalDate.of(2026, 9, 7),
                durationWeeks = 4,
                openForRegistration = true,
            )
        val field: Field = training.javaClass.superclass.getDeclaredField("id")
        field.isAccessible = true
        field.set(training, id)
        return training
    }
}
