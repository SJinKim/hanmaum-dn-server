package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.features.guide.api.v1.dto.WorshipCountRequest
import com.hanmaum.dn.app.features.guide.domain.GuideReportMode
import com.hanmaum.dn.app.features.guide.domain.WorshipCountReport
import com.hanmaum.dn.app.features.guide.repository.WorshipCountRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WorshipCountServiceTest {
    private val repository = mock<WorshipCountRepository>()
    private val service = WorshipCountService(repository)
    private val date = LocalDate.of(2026, 10, 18)

    @Test fun `explicit detailed mode is returned and summary writes cannot overwrite its totals`() {
        val report = report().apply { reportMode = GuideReportMode.DETAILED }
        whenever(repository.findByPublicIdAndDeletedAtIsNull(report.publicId)).thenReturn(report)
        whenever(repository.lockActive(report.publicId)).thenReturn(report)
        assertEquals("DETAILED", service.get(report.publicId).reportMode)
        assertFailsWith<GuideException> { service.update(report.publicId, WorshipCountRequest(date, 1, 2, 3, version = 0), "editor") }
        assertEquals(186, report.service1Count)
    }

    private fun report(
        date: LocalDate = this.date,
        a: Int? = 186,
        b: Int? = 384,
        c: Int? = 146,
    ) = WorshipCountReport(date, a, b, c, null, "test")

    @Test fun `total is int64 and missing service is not zero`() {
        assertEquals(6442450941L, report(a = Int.MAX_VALUE, b = Int.MAX_VALUE, c = Int.MAX_VALUE).total())
        assertNull(report(c = null).total())
        assertEquals(0L, report(a = 0, b = 0, c = 0).total())
    }

    @Test fun `delta uses exactly prior calendar week and reflects counts`() {
        val r = report()
        whenever(repository.findByPublicIdAndDeletedAtIsNull(r.publicId)).thenReturn(r)
        whenever(repository.findByServiceDateAndDeletedAtIsNull(date.minusWeeks(1))).thenReturn(report(date.minusWeeks(1), 171, 338, 144))
        assertEquals(63L, service.get(r.publicId).previousWeekDelta)
        whenever(repository.findByServiceDateAndDeletedAtIsNull(date.minusWeeks(1))).thenReturn(null)
        assertNull(service.get(r.publicId).previousWeekDelta)
    }

    @Test fun `stale versions and duplicate dates reject without changing report`() {
        val r = report()
        whenever(repository.lockActive(r.publicId)).thenReturn(r)
        assertFailsWith<GuideException> { service.update(r.publicId, WorshipCountRequest(date, 1, 2, 3, version = 1), "test") }
        assertEquals(186, r.service1Count)
        whenever(repository.findByServiceDateAndDeletedAtIsNull(date)).thenReturn(r)
        assertFailsWith<GuideException> { service.create(WorshipCountRequest(date, 1, 2, 3), "test") }
    }

    @Test fun `pagination passes range and page to repository instead of returning all rows`() {
        val secondPage =
            org.springframework.data.domain.PageRequest.of(
                1,
                2,
                org.springframework.data.domain.Sort
                    .by(org.springframework.data.domain.Sort.Direction.DESC, "serviceDate"),
            )
        whenever(repository.findRange(any(), any(), any<Pageable>())).thenReturn(PageImpl(listOf(report()), secondPage, 5))
        val result = service.list(null, null, 1, 2, "serviceDate,desc")
        assertEquals(5, result.totalElements)
        assertEquals(1, result.number)
        assertEquals(1, result.content.size)
        assertFailsWith<GuideException> { service.list(date, date.minusDays(1), 0, 20, "serviceDate,desc") }
        assertFailsWith<GuideException> { service.list(null, null, 0, 201, "serviceDate,desc") }
    }

    @Test fun `page deltas use one bulk query with exact previous dates including outside page range`() {
        val r1 = report()
        val r2 = report(date.plusWeeks(1), 0, 0, 0)
        val r3 = report(date.plusWeeks(2), 10, null, 0)
        val r4 = report(date.plusWeeks(3))
        val before = report(date.minusWeeks(1), 171, 338, 144)
        whenever(repository.findRange(any(), any(), any<Pageable>())).thenReturn(PageImpl(listOf(r1, r2, r3, r4)))
        val dates = setOf(date.minusWeeks(1), date, date.plusWeeks(1), date.plusWeeks(2))
        whenever(repository.findByServiceDateInAndDeletedAtIsNull(dates)).thenReturn(listOf(before, r1, r2, r3))
        val result = service.list(date, date.plusWeeks(3), 0, 200, "serviceDate,asc")
        assertEquals(listOf(63L, -716L, null, null), result.content.map { it.previousWeekDelta })
        verify(repository).findByServiceDateInAndDeletedAtIsNull(dates)
        verify(repository, never()).findByServiceDateAndDeletedAtIsNull(any())
    }

    @Test fun `empty page does not query previous weeks`() {
        whenever(repository.findRange(any(), any(), any<Pageable>())).thenReturn(PageImpl(emptyList()))
        assertEquals(0, service.list(null, null, 0, 20, "serviceDate,desc").content.size)
        verify(repository, never()).findByServiceDateInAndDeletedAtIsNull(any())
    }
}
