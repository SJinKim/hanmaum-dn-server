package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailEntryRequest
import com.hanmaum.dn.app.features.guide.guideAreas
import com.hanmaum.dn.app.features.guide.guideYouthExample
import com.hanmaum.dn.app.features.guide.guideZeroRows
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuideDetailsCalculatorTest {
    private val calculator = GuideDetailsCalculator()

    private fun calculate(entries: List<GuideDetailEntryRequest>) = calculator.calculate(entries, guideAreas)

    @Test fun `youth belongs to compatible adult count exactly once at row service room and day levels`() {
        val result = calculate(guideYouthExample())
        val row = result.entries.single { it.areaPublicId == guideAreas[0].areaPublicId && it.serviceNumber == 3 }.counts
        assertEquals(40L, row.adultCount)
        assertEquals(25L, row.youthCount)
        assertEquals(65L, row.compatibleAdultCount)
        assertEquals(5L, row.schoolCount)
        assertEquals(70L, row.totalCount)
        assertEquals(70L, result.services[2].counts.totalCount)
        assertEquals(70L, result.rooms[0].counts.totalCount)
        assertEquals(65L, result.totals.compatibleAdultCount)
        assertEquals(70L, result.totals.totalCount)
        assertTrue(result.totals.complete)
    }

    @Test fun `empty draft contains twelve unknown rows and zero is a separately recorded complete count`() {
        val empty = calculate(emptyList())
        assertEquals(12, empty.rows.size)
        assertTrue(empty.rows.all { it.adultCount == null && it.schoolCount == null && it.youthCount == null })
        assertNull(empty.totals.totalCount)
        assertFalse(empty.totals.complete)
        assertTrue(empty.services.all { it.counts.totalCount == null })
        val zero = calculate(guideZeroRows())
        assertEquals(0L, zero.totals.totalCount)
        assertTrue(zero.totals.complete)
        assertNull(zero.services[0].counts.youthCount)
        assertEquals(0L, zero.services[2].counts.youthCount)
    }

    @Test fun `missing required field makes only its dependent sums unknown without discarding other services`() {
        val rows =
            guideYouthExample().map {
                if (it.areaPublicId == guideAreas[1].areaPublicId &&
                    it.serviceNumber == 1
                ) {
                    it.copy(adultCount = null)
                } else {
                    it
                }
            }
        val result = calculate(rows)
        assertNull(result.services[0].counts.totalCount)
        assertEquals(0L, result.services[1].counts.totalCount)
        assertEquals(70L, result.services[2].counts.totalCount)
        assertEquals(5L, result.totals.schoolCount)
        assertNull(result.rooms[1].counts.totalCount)
        assertNull(result.totals.totalCount)
        assertFalse(result.totals.complete)
    }

    @Test fun `unknown removed rooms duplicate rows invalid services and youth outside third service fail with field paths`() {
        val cases =
            listOf(
                listOf(GuideDetailEntryRequest(java.util.UUID.randomUUID(), 1)) to "entries[0].areaPublicId",
                listOf(GuideDetailEntryRequest(guideAreas[0].areaPublicId, 4)) to "entries[0].serviceNumber",
                listOf(GuideDetailEntryRequest(guideAreas[0].areaPublicId, 1, youthCount = 0)) to "entries[0].youthCount",
                listOf(GuideDetailEntryRequest(guideAreas[0].areaPublicId, 2, adultCount = -1)) to "entries[0].adultCount",
                listOf(GuideDetailEntryRequest(guideAreas[0].areaPublicId, 3, schoolCount = -1)) to "entries[0].schoolCount",
                listOf(GuideDetailEntryRequest(guideAreas[0].areaPublicId, 3, youthCount = -1)) to "entries[0].youthCount",
                List(2) { GuideDetailEntryRequest(guideAreas[0].areaPublicId, 1) } to "entries[1]",
            )
        cases.forEach { (rows, field) ->
            assertTrue(field in assertFailsWith<GuideDetailValidationException> { calculate(rows) }.fieldErrors)
        }
    }

    @Test fun `service limits prevent overflow even in partial drafts but grand totals are int64`() {
        val max = guideZeroRows().map { if (it.areaPublicId == guideAreas[0].areaPublicId) it.copy(adultCount = Int.MAX_VALUE) else it }
        val result = calculate(max)
        assertEquals(6442450941L, result.totals.totalCount)
        assertTrue(result.services.all { it.counts.totalCount == 2147483647L })
        val completeOverflow =
            max.map {
                if (it.areaPublicId == guideAreas[0].areaPublicId &&
                    it.serviceNumber == 1
                ) {
                    it.copy(schoolCount = 1)
                } else {
                    it
                }
            }
        assertTrue(
            "services[1].totalCount" in
                assertFailsWith<GuideDetailValidationException> { calculate(completeOverflow) }.fieldErrors,
        )
        val partialOverflow = listOf(GuideDetailEntryRequest(guideAreas[0].areaPublicId, 3, adultCount = Int.MAX_VALUE, youthCount = 1))
        assertFailsWith<GuideDetailValidationException> { calculate(partialOverflow) }
    }

    @Test fun `room labels and order stay fixed independent of input order`() {
        val normal = calculate(guideYouthExample())
        val reversed = calculate(guideYouthExample().reversed())
        assertEquals(normal, reversed)
        assertEquals(listOf("본당1층A", "본당1층B", "본당2층C", "유아유치부"), normal.rooms.map { it.label })
        assertEquals(
            listOf(
                guideAreas[0].areaPublicId,
                guideAreas[1].areaPublicId,
                guideAreas[2].areaPublicId,
                guideAreas[3].areaPublicId,
            ),
            normal.rooms.map {
                it.areaPublicId
            },
        )
    }
}
