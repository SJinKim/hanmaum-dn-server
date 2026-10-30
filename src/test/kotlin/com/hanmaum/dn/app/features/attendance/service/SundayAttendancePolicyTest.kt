package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.features.attendance.service.SundayAttendancePolicy.Companion.absenceDays
import com.hanmaum.dn.app.features.attendance.service.SundayAttendancePolicy.Companion.absenceMonths
import com.hanmaum.dn.app.features.attendance.service.SundayAttendancePolicy.Companion.candidateUpperBound
import com.hanmaum.dn.app.features.attendance.service.SundayAttendancePolicy.Companion.isListed
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SundayAttendancePolicyTest {
    private fun d(text: String) = LocalDate.parse(text)

    @Test
    fun `listed from the day exactly three calendar months later, not the day before`() {
        val last = d("2026-07-26")

        assertFalse(isListed(last, d("2026-10-25")))
        assertTrue(isListed(last, d("2026-10-26")))
        assertEquals(92, absenceDays(last, d("2026-10-26")))
        assertEquals(3, absenceMonths(last, d("2026-10-26")))
        assertEquals(2, absenceMonths(last, d("2026-10-25")))
    }

    @Test
    fun `month-end anchor follows plusMonths, not today minus three months`() {
        val last = d("2027-01-31")

        assertFalse(isListed(last, d("2027-04-29")))
        assertTrue(isListed(last, d("2027-04-30")))
        assertEquals(3, absenceMonths(last, d("2027-04-30")))
        // The shortcut the design rules out would still exclude this day.
        assertFalse(!last.isAfter(d("2027-04-30").minusMonths(3)))
    }

    @Test
    fun `leap day anchor and six and twelve month jubilees are inclusive`() {
        val leap = d("2028-02-29")
        assertTrue(isListed(leap, d("2028-05-29")))
        assertEquals(12, absenceMonths(leap, d("2029-02-28")))

        val last = d("2026-03-29")
        assertEquals(5, absenceMonths(last, d("2026-09-28")))
        assertEquals(6, absenceMonths(last, d("2026-09-29")))
        assertEquals(11, absenceMonths(last, d("2027-03-28")))
        assertEquals(12, absenceMonths(last, d("2027-03-29")))
    }

    @Test
    fun `the loose SQL bound never drops a listed member`() {
        generateSequence(d("2026-01-01")) { it.plusDays(1) }
            .takeWhile { it.isBefore(d("2028-12-31")) }
            .forEach { last ->
                assertTrue(!last.isAfter(candidateUpperBound(last.plusMonths(3))), "anchor $last")
            }
    }

    @Test
    fun `configured definition ids are parsed and rendered as a postgres array`() {
        val a = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val b = UUID.fromString("22222222-2222-2222-2222-222222222222")

        val policy = SundayAttendancePolicy(" $a , $b ,")

        assertEquals(setOf(a, b), policy.sundayServiceDefinitionIds)
        assertEquals("{$a,$b}", policy.sundayServiceIdsParameter)
        assertEquals("{}", SundayAttendancePolicy("").sundayServiceIdsParameter)
    }
}
