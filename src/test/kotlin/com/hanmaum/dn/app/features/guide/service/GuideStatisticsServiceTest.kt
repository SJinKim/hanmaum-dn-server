package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideStatisticKey
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideStatisticNullReason
import com.hanmaum.dn.app.features.guide.domain.WorshipCountReport
import com.hanmaum.dn.app.features.guide.repository.WorshipCountRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuideStatisticsServiceTest {
    private val repository = mock<WorshipCountRepository>()
    private val clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val service = GuideStatisticsService(repository, clock)

    private fun row(
        date: String,
        a: Int?,
        b: Int? = 0,
        c: Int? = 0,
    ) = WorshipCountReport(LocalDate.parse(date), a, b, c, null, "test")

    private fun source(vararg rows: WorshipCountReport) {
        whenever(repository.findAllByServiceDateBetweenAndDeletedAtIsNullOrderByServiceDateAsc(any(), any())).thenAnswer { call ->
            val from = call.getArgument<LocalDate>(0)
            val to = call.getArgument<LocalDate>(1)
            rows.filter { it.serviceDate in from..to && it.deletedAt == null }.sortedBy { it.serviceDate }
        }
    }

    @Test fun `same complete cohort drives sums means shares and changes while zero is recorded`() {
        source(
            row("2025-01-05", 5, 10, 5),
            row("2025-11-02", 100),
            row("2026-01-04", 10, 20, 10),
            row("2026-01-11", 0),
            row("2026-01-18", 999, null),
            row("2026-01-25", 500),
        )
        val result = service.statistics(2026, LocalDate.parse("2026-01-18"))
        assertEquals(3, result.current.reportCount)
        assertEquals(2, result.current.completeCount)
        assertEquals(1, result.current.incompleteCount)
        assertEquals(listOf(10L, 20L, 10L, 40L), result.current.metrics.map { it.sum })
        assertEquals(listOf(BigDecimal(5), BigDecimal(10), BigDecimal(5), BigDecimal(20)), result.current.metrics.map { it.average })
        assertEquals(
            listOf(BigDecimal(25), BigDecimal(50), BigDecimal(25), BigDecimal(100)),
            result.current.metrics.map { it.sharePercent },
        )
        assertEquals(BigDecimal.ZERO, result.changesFromSamePeriod.last().absoluteChange)
        assertEquals(BigDecimal.ZERO, result.changesFromSamePeriod.last().percentChange)
        assertEquals(1, result.previousSamePeriod.completeCount)
        assertEquals(2, result.previousFullYear.completeCount)
        assertEquals(3, result.timeline.size)
        assertNull(result.timeline.last().totalCount)
        assertEquals(999, result.timeline.last().service1Count)
        verify(repository, times(1)).findAllByServiceDateBetweenAndDeletedAtIsNullOrderByServiceDateAsc(any(), any())
    }

    @Test fun `zero denominator has an explicit reason and never Infinity`() {
        source(row("2025-01-05", 0), row("2026-01-04", 10))
        val result = service.statistics(2026, LocalDate.parse("2026-01-10"))
        assertEquals(
            GuideStatisticNullReason.ZERO_TOTAL,
            result.previousSamePeriod.metrics
                .last()
                .shareReason,
        )
        assertEquals(
            BigDecimal.ZERO,
            result.previousSamePeriod.metrics
                .last()
                .average,
        )
        assertEquals(BigDecimal(10), result.changesFromSamePeriod.last().absoluteChange)
        assertNull(result.changesFromSamePeriod.last().percentChange)
        assertEquals(GuideStatisticNullReason.ZERO_BASELINE, result.changesFromSamePeriod.last().percentReason)
    }

    @Test fun `no complete current cohort and no baseline remain distinct null reasons`() {
        source(row("2026-01-04", null, 100))
        val result = service.statistics(2026, null)
        assertEquals(1, result.current.incompleteCount)
        assertNull(
            result.current.metrics
                .last()
                .sum,
        )
        assertEquals(
            GuideStatisticNullReason.NO_COMPLETE_REPORTS,
            result.current.metrics
                .last()
                .averageReason,
        )
        assertEquals(GuideStatisticNullReason.NO_CURRENT_DATA, result.changesFromSamePeriod.last().percentReason)
        source(row("2026-01-04", 0))
        assertEquals(
            GuideStatisticNullReason.NO_BASELINE_DATA,
            service
                .statistics(2026, null)
                .changesFromSamePeriod
                .last()
                .percentReason,
        )
    }

    @Test fun `leap cutoff clamps February and includes both endpoints without filling missing weeks`() {
        source(row("2023-02-28", 20), row("2023-03-01", 100), row("2024-01-01", 10), row("2024-02-29", 30), row("2024-03-01", 100))
        val result = service.statistics(2024, LocalDate.parse("2024-02-29"))
        assertEquals(LocalDate.parse("2023-02-28"), result.previousSamePeriod.to)
        assertEquals(
            20L,
            result.previousSamePeriod.metrics
                .last()
                .sum,
        )
        assertEquals(
            40L,
            result.current.metrics
                .last()
                .sum,
        )
        assertEquals(2, result.timeline.size)
        assertEquals(LocalDate.parse("2024-01-01"), result.current.firstCompleteDate)
        assertEquals(LocalDate.parse("2024-02-29"), result.current.lastCompleteDate)
    }

    @Test fun `default cutoff is the latest report up to church today and historical empty years remain valid`() {
        source(row("2026-10-18", 1), row("2026-10-25", 999))
        assertEquals(LocalDate.parse("2026-10-18"), service.statistics(2026, null).asOf)
        source()
        val empty = service.statistics(2024, null)
        assertEquals(LocalDate.parse("2024-12-31"), empty.asOf)
        assertEquals(0, empty.current.reportCount)
        assertTrue(empty.timeline.isEmpty())
        assertNull(empty.current.firstReportDate)
    }

    @Test fun `default year boundary follows the injected church time zone`() {
        source()
        val berlin = Clock.fixed(Instant.parse("2026-12-31T23:30:00Z"), ZoneId.of("Europe/Berlin"))
        val result = GuideStatisticsService(repository, berlin).statistics(2027, null)
        assertEquals(LocalDate.parse("2027-01-01"), result.asOf)
        assertEquals("Europe/Berlin", result.timeZone)
    }

    @Test fun `invalid year and mismatched cutoff are rejected before querying`() {
        for (year in listOf(0, 1, 10000, 2027)) assertFailsWith<GuideException> { service.statistics(year, null) }
        assertFailsWith<GuideException> { service.statistics(2026, LocalDate.parse("2025-01-01")) }
    }

    @Test fun `changes and soft deletes are reflected on the next snapshot`() {
        val old = row("2026-01-04", 10)
        source(old)
        assertEquals(
            10L,
            service
                .statistics(2026, null)
                .current.metrics
                .last()
                .sum,
        )
        old.service1Count = 25
        assertEquals(
            25L,
            service
                .statistics(2026, null)
                .current.metrics
                .last()
                .sum,
        )
        old.deletedAt = Instant.now(clock)
        assertNull(
            service
                .statistics(2026, null)
                .current.metrics
                .last()
                .sum,
        )
    }

    @Test fun `reference annual totals use calendar coverage rather than matching row counts`() {
        val prior = (0L..51L).map { i -> row(LocalDate.parse("2025-01-05").plusWeeks(i).toString(), if (i == 51L) 815 else 775) }
        val current = (0L..41L).map { i -> row(LocalDate.parse("2026-01-04").plusWeeks(i).toString(), if (i == 41L) 685 else 750) }
        source(*(prior + current).toTypedArray())
        val result = service.statistics(2026, LocalDate.parse("2026-10-18"))
        assertEquals(42, result.current.completeCount)
        assertEquals(
            31435L,
            result.current.metrics
                .last()
                .sum,
        )
        assertTrue(
            result.current.metrics
                .last()
                .average!!
                .subtract(BigDecimal("748.452380952380952380952380952381"))
                .abs() <
                BigDecimal("0.00000000000000000000000000001"),
        )
        assertEquals(52, result.previousFullYear.completeCount)
        assertEquals(
            40340L,
            result.previousFullYear.metrics
                .last()
                .sum,
        )
        assertEquals(41, result.previousSamePeriod.completeCount)
        assertEquals(
            31775L,
            result.previousSamePeriod.metrics
                .last()
                .sum,
        )
        assertEquals(
            GuideStatisticKey.TOTAL,
            result.current.metrics
                .last()
                .key,
        )
    }

    @Test fun `totals promote to long before summation`() {
        source(row("2026-01-04", Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(
            6442450941L,
            service
                .statistics(2026, null)
                .current.metrics
                .last()
                .sum,
        )
    }
}
