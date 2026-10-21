package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsResponse
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideHomeStatus
import com.hanmaum.dn.app.features.guide.api.v1.dto.WorshipCountResponse
import com.hanmaum.dn.app.features.guide.domain.GuideReportMode
import com.hanmaum.dn.app.features.guide.domain.WorshipCountReport
import com.hanmaum.dn.app.features.guide.repository.GuideWorshipDetailRepository
import com.hanmaum.dn.app.features.guide.repository.WorshipCountRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuideHomeServiceTest {
    private val reports = mock<WorshipCountRepository>()
    private val rows = mock<GuideWorshipDetailRepository>()
    private val details = mock<GuideDetailsService>()
    private val clock = Clock.fixed(Instant.parse("2026-10-18T08:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val service = GuideHomeService(reports, details, clock)
    private val sunday = LocalDate.of(2026, 10, 18)

    private fun report(
        weeksAgo: Long = 0,
        total: Int? = 10,
    ) = WorshipCountReport(
        sunday.minusWeeks(weeksAgo),
        total,
        if (total ==
            null
        ) {
            null
        } else {
            0
        },
        if (total == null) null else 0,
        "note\nline",
        "test",
    )

    private fun source(vararg source: WorshipCountReport) {
        whenever(reports.findAllByServiceDateLessThanEqualAndDeletedAtIsNullOrderByServiceDateDesc(any(), any())).thenReturn(source.take(4))
        whenever(reports.findHomeConfirmed(any(), any())).thenAnswer {
            source
                .filter {
                    it.total() != null ||
                        listOf(it.service1Count, it.service2Count, it.service3Count).any { count -> count != null } ||
                        it.publicId in rows.findConfirmedReportIds(listOf(it.publicId))
                }.take(1)
        }
        whenever(reports.findHomeCompleteSundays(any(), any())).thenReturn(
            source
                .filter {
                    it.serviceDate.dayOfWeek == DayOfWeek.SUNDAY &&
                        it.total() != null
                }.take(4),
        )
        whenever(reports.findRange(any(), any(), any())).thenReturn(PageImpl(source.filter { it.serviceDate >= sunday.minusWeeks(7) }))
        whenever(reports.findByServiceDateInAndDeletedAtIsNull(any())).thenReturn(source.toList())
    }

    @Test fun `empty Sunday retains eight unknown slots without inventing zero or detail`() {
        source()
        val home = service.get()
        assertEquals(GuideHomeStatus.EMPTY, home.status)
        assertNull(home.attendance)
        assertTrue(home.waitingForCurrentSunday)
        assertEquals(8, home.timeline.size)
        assertEquals(sunday.minusWeeks(7), home.timeline.first().serviceDate)
        assertEquals(sunday, home.timeline.last().serviceDate)
        assertTrue(home.timeline.all { it.totalCount == null })
        assertNull(home.metrics.meanLastFour)
        assertEquals(0, home.metrics.meanReportCount)
        assertEquals("NO_CURRENT_DATA", home.metrics.percentReason)
    }

    @Test fun `historical summary remains complete with actual date and exact independently dated metrics`() {
        val current = WorshipCountReport(sunday, 186, 384, 146, "note\nline", "test")
        source(current, report(1, 653), report(2, 680), report(3, 773), report(4, 900))
        val home = service.get()
        assertEquals(GuideHomeStatus.COMPLETE, home.status)
        assertEquals("SUMMARY_ONLY", home.attendance?.report?.reportMode)
        assertTrue(home.attendance!!.entries.isEmpty())
        assertNull(home.attendance.totals)
        assertFalse(home.waitingForCurrentSunday)
        assertEquals(716L, home.metrics.totalCount)
        assertEquals(63L, home.metrics.absoluteChange)
        assertEquals(sunday.minusWeeks(1), home.metrics.referenceDate)
        assertTrue(
            home.metrics.percentChange!!
                .subtract(BigDecimal("9.647779479326187"))
                .abs() < BigDecimal("0.000000001"),
        )
        assertEquals(BigDecimal("705.5"), home.metrics.meanLastFour)
        assertEquals(4, home.metrics.meanReportCount)
        assertEquals(listOf(716L, 653L, 680L, 773L), home.lastFourReports.map { it.totalCount })
        assertEquals(186, home.shares[0].count)
        assertEquals(sunday, home.sharesReportDate)
        assertNull(home.sharesReason)
    }

    @Test fun `new blank draft keeps previous attendance date but provides resume UUID`() {
        val draft = report(total = null).also { it.reportMode = GuideReportMode.DETAILED }
        val previous = report(1, 7)
        source(draft, previous)
        whenever(rows.findConfirmedReportIds(listOf(draft.publicId))).thenReturn(emptyList())
        val home = service.get()
        assertEquals(previous.serviceDate, home.attendance?.report?.serviceDate)
        assertEquals(draft.publicId, home.currentSundayReport?.publicId)
        assertTrue(home.waitingForCurrentSunday)
        assertEquals(previous.serviceDate, home.metrics.reportDate)
    }

    @Test fun `one detailed cell replaces fallback even if all canonical service totals remain unknown`() {
        val partial = report(total = null).also { it.reportMode = GuideReportMode.DETAILED }
        source(partial, report(1, 7))
        whenever(rows.findConfirmedReportIds(listOf(partial.publicId))).thenReturn(listOf(partial.publicId))
        whenever(details.getForHome(partial.publicId)).thenReturn(
            GuideDetailsResponse(
                WorshipCountResponse(partial.publicId, sunday, null, null, null, null, null, null, 0, "DETAILED", false),
                emptyList(),
                emptyList(),
                emptyList(),
                null,
            ),
        )
        val home = service.get()
        assertEquals(GuideHomeStatus.PARTIAL, home.status)
        assertEquals(sunday, home.attendance?.report?.serviceDate)
        assertFalse(home.waitingForCurrentSunday)
        assertNull(home.attendance?.report?.totalCount)
        assertEquals(sunday.minusWeeks(1), home.metrics.reportDate)
        assertEquals("NO_COMPLETE_REPORT", home.sharesReason)
    }

    @Test fun `partial summary replaces fallback and real zero is complete with undefined shares and zero baseline percent`() {
        val partial = report().also { it.service2Count = null }
        source(partial, report(1, 0), report(3, 0))
        val home = service.get()
        assertEquals(GuideHomeStatus.PARTIAL, home.status)
        assertEquals(sunday.minusWeeks(1), home.metrics.reportDate)
        assertEquals(sunday.minusWeeks(3), home.metrics.referenceDate)
        assertEquals(0L, home.metrics.absoluteChange)
        assertNull(home.metrics.percentChange)
        assertEquals("ZERO_BASELINE", home.metrics.percentReason)
        assertNull(home.timeline[5].totalCount) // missing Sunday between complete reports
        source(report(total = 0))
        val zero = service.get()
        assertEquals(GuideHomeStatus.COMPLETE, zero.status)
        assertEquals(0L, zero.attendance?.report?.totalCount)
        assertEquals("ZERO_TOTAL", zero.sharesReason)
        assertTrue(zero.shares.all { it.percent == null && it.count == 0 })
    }

    @Test fun `old corrections refresh metrics and snapshot version without selecting old updatedAt`() {
        val current = report(total = 8)
        val old = report(1, 5)
        source(current, old)
        val before = service.get()
        old.service1Count = 7
        old.version++
        old.updatedAt = clock.instant().plusSeconds(100)
        val after = service.get()
        assertEquals(current.publicId, after.attendance?.report?.publicId)
        assertEquals(1L, after.metrics.absoluteChange)
        assertNotEquals(before.snapshotVersion, after.snapshotVersion)
        assertEquals(after.snapshotVersion, service.get().snapshotVersion)
    }

    @Test fun `metrics use complete Sundays while the attendance date can be a weekday`() {
        val weekday = report(total = 1000).also { it.serviceDate = sunday.minusDays(1) }
        source(weekday, report(1, 7))
        val home = service.get()
        assertEquals(weekday.serviceDate, home.attendance?.report?.serviceDate)
        assertEquals(sunday.minusWeeks(1), home.metrics.reportDate)
        assertEquals(1, home.metrics.meanReportCount)
    }

    @Test fun `church timezone determines Sunday even when UTC is still Saturday`() {
        val churchClock = Clock.fixed(Instant.parse("2026-10-17T22:30:00Z"), ZoneId.of("Europe/Berlin"))
        source()
        val home = GuideHomeService(reports, details, churchClock).get()
        assertEquals(sunday, home.currentSunday)
        assertEquals(churchClock.instant(), home.asOf)
        assertEquals("Europe/Berlin", home.timeZone)
        val utc = GuideHomeService(reports, details, churchClock.withZone(ZoneId.of("UTC"))).get()
        assertEquals(sunday.minusWeeks(1), utc.currentSunday)
    }
}
