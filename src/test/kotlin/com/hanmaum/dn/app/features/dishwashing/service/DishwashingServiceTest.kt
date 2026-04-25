package com.hanmaum.dn.app.features.dishwashing.service

import com.hanmaum.dn.app.features.dishwashing.api.v1.dto.CreateDishwashingRequest
import com.hanmaum.dn.app.features.dishwashing.domain.DishwashingSchedule
import com.hanmaum.dn.app.features.dishwashing.repository.DishwashingRepository
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class DishwashingServiceTest {
    @Mock
    private lateinit var dishwashingRepository: DishwashingRepository

    @Mock
    private lateinit var churchGroupRepository: ChurchGroupRepository

    @InjectMocks
    private lateinit var dishwashingService: DishwashingService

    private fun group(
        id: Long,
        name: String,
    ): ChurchGroup {
        val g = ChurchGroup(name = name)
        g.id = id
        return g
    }

    private fun schedule(
        date: LocalDate,
        group: ChurchGroup,
        note: String? = null,
    ): DishwashingSchedule = DishwashingSchedule(scheduledDate = date, group = group, note = note)

    // --- getUpcomingSchedule ---

    @Test
    fun `getUpcomingSchedule returns empty list when no schedules`() {
        `when`(dishwashingRepository.findAllByScheduledDateGreaterThanEqualOrderByScheduledDateAsc(any<LocalDate>()))
            .thenReturn(emptyList())

        assertTrue(dishwashingService.getUpcomingSchedule().isEmpty())
    }

    @Test
    fun `getUpcomingSchedule groups multiple entries by date into one DishwashingDayDto`() {
        val date1 = LocalDate.of(2026, 4, 5)
        val date2 = LocalDate.of(2026, 4, 12)
        val g1 = group(1L, "사랑조")
        val g2 = group(2L, "믿음조")
        val g3 = group(3L, "소망조")

        `when`(dishwashingRepository.findAllByScheduledDateGreaterThanEqualOrderByScheduledDateAsc(any<LocalDate>()))
            .thenReturn(
                listOf(
                    schedule(date1, g1, "특별 행사"),
                    schedule(date1, g2, "특별 행사"),
                    schedule(date2, g3),
                ),
            )

        val result = dishwashingService.getUpcomingSchedule()

        assertEquals(2, result.size)

        val day1 = result.first { it.date == date1 }
        assertEquals(2, day1.groupNames.size)
        assertTrue(day1.groupNames.containsAll(listOf("사랑조", "믿음조")))
        assertEquals("특별 행사", day1.note)

        val day2 = result.first { it.date == date2 }
        assertEquals(listOf("소망조"), day2.groupNames)
        assertNull(day2.note)
    }

    @Test
    fun `getUpcomingSchedule takes note from first entry of each day`() {
        val date = LocalDate.of(2026, 4, 5)
        `when`(dishwashingRepository.findAllByScheduledDateGreaterThanEqualOrderByScheduledDateAsc(any<LocalDate>()))
            .thenReturn(
                listOf(
                    schedule(date, group(1L, "A"), "첫 번째 노트"),
                    schedule(date, group(2L, "B"), "두 번째 노트"),
                ),
            )

        val result = dishwashingService.getUpcomingSchedule()

        assertEquals("첫 번째 노트", result.first().note)
    }

    // --- createSchedule ---

    @Test
    fun `createSchedule deletes existing entries for the date before saving`() {
        val date = LocalDate.of(2026, 4, 5)
        val req = CreateDishwashingRequest(date = date, groupIds = listOf(1L))
        `when`(churchGroupRepository.findAllById(listOf(1L))).thenReturn(listOf(group(1L, "A")))
        `when`(dishwashingRepository.saveAll(any<Iterable<DishwashingSchedule>>())).thenReturn(emptyList())

        dishwashingService.createSchedule(req)

        verify(dishwashingRepository).deleteAllByScheduledDate(date)
    }

    @Test
    fun `createSchedule throws EntityNotFoundException when some groups not found`() {
        val req = CreateDishwashingRequest(date = LocalDate.of(2026, 4, 5), groupIds = listOf(1L, 2L))
        `when`(churchGroupRepository.findAllById(listOf(1L, 2L))).thenReturn(listOf(group(1L, "A")))

        assertThrows<EntityNotFoundException> { dishwashingService.createSchedule(req) }
    }

    @Test
    fun `createSchedule saves one entry per group with correct date and note`() {
        val date = LocalDate.of(2026, 4, 5)
        val req = CreateDishwashingRequest(date = date, groupIds = listOf(1L, 2L), note = "메모")
        `when`(churchGroupRepository.findAllById(listOf(1L, 2L))).thenReturn(listOf(group(1L, "A"), group(2L, "B")))
        val captor = argumentCaptor<Iterable<DishwashingSchedule>>()
        `when`(dishwashingRepository.saveAll(any<Iterable<DishwashingSchedule>>())).thenReturn(emptyList())

        dishwashingService.createSchedule(req)

        verify(dishwashingRepository).saveAll(captor.capture())
        val saved = captor.firstValue.toList()
        assertEquals(2, saved.size)
        assertTrue(saved.all { it.note == "메모" })
        assertTrue(saved.all { it.scheduledDate == date })
    }

    // --- deleteScheduleForDate ---

    @Test
    fun `deleteScheduleForDate delegates to repository`() {
        val date = LocalDate.of(2026, 4, 5)

        dishwashingService.deleteScheduleForDate(date)

        verify(dishwashingRepository).deleteAllByScheduledDate(date)
    }
}
