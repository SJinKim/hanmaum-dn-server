package com.hanmaum.dn.app.features.verses.service

import com.hanmaum.dn.app.common.domainvalue.VerseRecordKind
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.verses.client.BibleApiClient
import com.hanmaum.dn.app.features.verses.client.BibleApiUnavailableException
import com.hanmaum.dn.app.features.verses.client.QuietTimeItem
import com.hanmaum.dn.app.features.verses.domain.WeeklyVerse
import com.hanmaum.dn.app.features.verses.repository.VerseRecordRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class VerseRecordServiceTest {
    @Mock private lateinit var recordRepository: VerseRecordRepository

    @Mock private lateinit var memberRepository: MemberRepository

    @Mock private lateinit var verseService: VerseService

    @Mock private lateinit var client: BibleApiClient

    private val zone = ZoneId.of("Europe/Berlin")
    private val weekStart = LocalDate.of(2026, 9, 6)

    private fun member(): Member = Member(lastName = "김", firstName = "철수").apply { id = 1L }

    /** Tuesday 2026-09-08 by default; the Sunday starting its week is 2026-09-06. */
    private fun service(instant: String = "2026-09-08T09:00:00Z"): VerseRecordService {
        val clock = Clock.fixed(Instant.parse(instant), zone)
        lenient().`when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member())
        lenient().`when`(verseService.weekStartOf(any())).thenReturn(weekStart)
        return VerseRecordService(recordRepository, memberRepository, verseService, client, clock)
    }

    private fun weeklyVerse() =
        WeeklyVerse(weekStart = weekStart, book = 43, chapterStart = 1, verseStart = 5, chapterEnd = 1, verseEnd = 5)

    @Test
    fun `records carry only the running week plus an all-time total`() {
        val today = LocalDate.of(2026, 9, 8)
        `when`(verseService.currentWeeklyVerse()).thenReturn(weeklyVerse())
        `when`(client.quietTime(today)).thenReturn(QuietTimeItem(book = 5, chapterStart = 3, verseStart = 1))
        `when`(recordRepository.findDatesInRange(eq(1L), eq("QUIET_TIME"), any(), any()))
            .thenReturn(listOf(LocalDate.of(2026, 9, 7), today))
        `when`(recordRepository.findDatesInRange(eq(1L), eq("RECITATION"), any(), any()))
            .thenReturn(listOf(LocalDate.of(2026, 9, 7)))
        `when`(recordRepository.countForMember(1L, "QUIET_TIME")).thenReturn(84L)
        `when`(recordRepository.countForMember(1L, "RECITATION")).thenReturn(127L)

        val result = service().getRecords("kc-001")

        // Seven pills are all the client draws; years of history would otherwise travel for
        // a seven-day strip. The long number rides along as a count instead.
        assertEquals(listOf(LocalDate.of(2026, 9, 7), today), result.quietTime.days)
        assertEquals(84L, result.quietTime.totalDays)
        assertTrue(result.quietTime.todayMarked)
        assertEquals(127L, result.recitation.totalDays)
        assertFalse(result.recitation.todayMarked)
    }

    @Test
    fun `sunday is not markable for the daily passage, without asking upstream`() {
        // Sunday 2026-09-06. The reading plan has no passage on Sundays — measured on four
        // of them — and that much is decidable locally.
        val result = service("2026-09-06T09:00:00Z").getRecords("kc-001")

        assertFalse(result.quietTime.todayMarkable)
        verify(client, never()).quietTime(any())
    }

    @Test
    fun `a day the plan skips is not markable`() {
        val today = LocalDate.of(2026, 9, 8)
        `when`(client.quietTime(today)).thenReturn(null)

        assertFalse(service().getRecords("kc-001").quietTime.todayMarkable)
    }

    @Test
    fun `recitation is not markable while no verse has been chosen`() {
        `when`(verseService.currentWeeklyVerse()).thenReturn(null)

        // An empty card has nothing to recite, so offering the tick would be a lie.
        assertFalse(service().getRecords("kc-001").recitation.todayMarkable)
    }

    @Test
    fun `an unreachable upstream still lets a member mark the day`() {
        val today = LocalDate.of(2026, 9, 8)
        `when`(client.quietTime(today)).thenThrow(BibleApiUnavailableException("down"))

        // Refusing to record something the member actually did, because a third party is
        // down, is the worse of the two mistakes.
        assertTrue(service().getRecords("kc-001").quietTime.todayMarkable)
    }

    @Test
    fun `recitation follows the week of the chosen verse`() {
        val verseWeek = LocalDate.of(2026, 8, 30)
        `when`(verseService.currentWeeklyVerse()).thenReturn(
            WeeklyVerse(weekStart = verseWeek, book = 43, chapterStart = 1, verseStart = 5, chapterEnd = 1, verseEnd = 5),
        )

        assertEquals(verseWeek, service().getRecords("kc-001").recitation.weekStart)
    }

    @Test
    fun `marking today writes one row and returns the refreshed block`() {
        val today = LocalDate.of(2026, 9, 8)
        `when`(verseService.currentWeeklyVerse()).thenReturn(weeklyVerse())
        `when`(recordRepository.insertIfAbsent(any(), eq(1L), eq(today), eq("RECITATION"))).thenReturn(1)
        `when`(recordRepository.findDatesInRange(eq(1L), eq("RECITATION"), any(), any())).thenReturn(listOf(today))
        `when`(recordRepository.countForMember(1L, "RECITATION")).thenReturn(1L)

        val block = service().mark("kc-001", VerseRecordKind.RECITATION)

        assertTrue(block.todayMarked)
        assertEquals(1L, block.totalDays)
    }

    @Test
    fun `a second mark on the same day conflicts in the database, not in a race`() {
        val today = LocalDate.of(2026, 9, 8)
        `when`(verseService.currentWeeklyVerse()).thenReturn(weeklyVerse())
        `when`(recordRepository.insertIfAbsent(any(), eq(1L), eq(today), eq("RECITATION"))).thenReturn(0)

        val exception =
            assertThrows<ResponseStatusException> { service().mark("kc-001", VerseRecordKind.RECITATION) }

        // 409, which the client treats as success — the member's intent already holds.
        assertEquals(409, exception.statusCode.value())
    }

    @Test
    fun `marking a sunday for the daily passage is refused rather than written`() {
        val exception =
            assertThrows<ResponseStatusException> {
                service("2026-09-06T09:00:00Z").mark("kc-001", VerseRecordKind.QUIET_TIME)
            }

        assertEquals(400, exception.statusCode.value())
        verify(recordRepository, never()).insertIfAbsent(any(), any(), any(), any())
    }

    @Test
    fun `marking recitation without a chosen verse is refused`() {
        `when`(verseService.currentWeeklyVerse()).thenReturn(null)

        assertThrows<ResponseStatusException> { service().mark("kc-001", VerseRecordKind.RECITATION) }

        verify(recordRepository, never()).insertIfAbsent(any(), any(), any(), any())
    }
}
