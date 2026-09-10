package com.hanmaum.dn.app.features.verses.service

import com.hanmaum.dn.app.common.domainvalue.VerseRecordKind
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.members.service.MemberPrincipal
import com.hanmaum.dn.app.features.members.service.MemberProfileNotFoundException
import com.hanmaum.dn.app.features.verses.client.BibleApiClient
import com.hanmaum.dn.app.features.verses.client.BibleApiUnavailableException
import com.hanmaum.dn.app.features.verses.client.QuietTimeItem
import com.hanmaum.dn.app.features.verses.domain.WeeklyVerse
import com.hanmaum.dn.app.features.verses.repository.VerseRecordCount
import com.hanmaum.dn.app.features.verses.repository.VerseRecordMark
import com.hanmaum.dn.app.features.verses.repository.VerseRecordRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.never
import org.mockito.Mockito.times
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
    private val tuesday = LocalDate.of(2026, 9, 8)

    private val caller = MemberPrincipal(subject = "kc-001", email = "a@example.com", emailVerified = true)

    private fun member(): Member = Member(lastName = "김", firstName = "철수").apply { id = 1L }

    // Defaults live here, not in service(), so a test's own stubbing runs afterwards and
    // wins. Mockito already answers an empty list for the two list-returning queries, so
    // only the two that would otherwise answer null are set.
    @BeforeEach
    fun setUp() {
        lenient().`when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(member())
        lenient().`when`(verseService.weekStartOf(any())).thenReturn(weekStart)
    }

    /** Tuesday 2026-09-08 by default; the Sunday starting its week is 2026-09-06. */
    private fun service(instant: String = "2026-09-08T09:00:00Z"): VerseRecordService =
        VerseRecordService(
            recordRepository,
            CurrentMemberResolver(memberRepository),
            verseService,
            client,
            Clock.fixed(Instant.parse(instant), zone),
        )

    private fun weeklyVerse(week: LocalDate = weekStart) =
        WeeklyVerse(weekStart = week, book = 43, chapterStart = 1, verseStart = 5, chapterEnd = 1, verseEnd = 5)

    // ─── The blocker ───────────────────────────────────────────────────────────

    @Test
    fun `a member with no marks at all still gets both blocks`() {
        `when`(verseService.currentWeeklyVerse()).thenReturn(weeklyVerse())
        `when`(client.quietTime(tuesday)).thenReturn(QuietTimeItem(book = 5, chapterStart = 3, verseStart = 1))

        // This is what 0.8.0 could not render. Seven empty pills is a valid answer, not an
        // error — a member who has never marked anything still has a streak card.
        val result = service().getRecords(caller)

        assertEquals(emptyList(), result.quietTime.days)
        assertEquals(0L, result.quietTime.totalDays)
        assertEquals(0L, result.recitation.totalDays)
        assertFalse(result.quietTime.todayMarked)
    }

    @Test
    fun `an account without a member profile fails distinguishably`() {
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(null)
        `when`(memberRepository.findByEmailAndDeletedAtIsNull("a@example.com")).thenReturn(null)

        // Not a bare 404: a missing route and a person who is not a member of this
        // congregation are different problems and the client does different things about
        // them. Looking for a deployed-but-invisible endpoint cost an afternoon once.
        assertThrows<MemberProfileNotFoundException> { service().getRecords(caller) }
    }

    @Test
    fun `a legacy account is adopted here exactly as it is on the profile endpoint`() {
        val legacy = Member(lastName = "김", firstName = "영희").apply { id = 2L }
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull("kc-001")).thenReturn(null)
        `when`(memberRepository.findByEmailAndDeletedAtIsNull("a@example.com")).thenReturn(legacy)
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }
        `when`(verseService.currentWeeklyVerse()).thenReturn(weeklyVerse())
        `when`(client.quietTime(tuesday)).thenReturn(QuietTimeItem(book = 5, chapterStart = 3, verseStart = 1))

        // The two readers used to disagree: an account that worked on /members/me threw
        // here, which is the whole reason the bars stayed invisible.
        service().getRecords(caller)

        assertEquals("kc-001", legacy.keycloakId)
    }

    // ─── Query shape ───────────────────────────────────────────────────────────

    @Test
    fun `the weekly verse is resolved once per request, not once per block`() {
        `when`(verseService.currentWeeklyVerse()).thenReturn(weeklyVerse())
        `when`(client.quietTime(tuesday)).thenReturn(QuietTimeItem(book = 5, chapterStart = 3, verseStart = 1))

        service().getRecords(caller)

        // It used to be looked up twice inside the recitation block alone — once for the
        // week bounds, once for markability.
        verify(verseService, times(1)).currentWeeklyVerse()
    }

    @Test
    fun `both streaks are read with one range query and one grouped count`() {
        `when`(verseService.currentWeeklyVerse()).thenReturn(weeklyVerse())
        `when`(client.quietTime(tuesday)).thenReturn(QuietTimeItem(book = 5, chapterStart = 3, verseStart = 1))

        service().getRecords(caller)

        verify(recordRepository, times(1)).findMarksInRange(any(), any(), any())
        verify(recordRepository, times(1)).countByKind(any())
    }

    @Test
    fun `marks are split by kind and clipped to each streak's own week`() {
        val verseWeek = LocalDate.of(2026, 8, 30)
        `when`(verseService.currentWeeklyVerse()).thenReturn(weeklyVerse(verseWeek))
        `when`(client.quietTime(tuesday)).thenReturn(QuietTimeItem(book = 5, chapterStart = 3, verseStart = 1))
        `when`(recordRepository.findMarksInRange(eq(1L), any(), any())).thenReturn(
            listOf(
                VerseRecordMark(LocalDate.of(2026, 9, 7), VerseRecordKind.QUIET_TIME),
                VerseRecordMark(LocalDate.of(2026, 8, 31), VerseRecordKind.RECITATION),
                // Inside the span the query covers, but outside the recitation week.
                VerseRecordMark(LocalDate.of(2026, 9, 9), VerseRecordKind.RECITATION),
            ),
        )
        `when`(recordRepository.countByKind(1L)).thenReturn(
            listOf(
                VerseRecordCount(VerseRecordKind.QUIET_TIME, 84L),
                VerseRecordCount(VerseRecordKind.RECITATION, 127L),
            ),
        )

        val result = service().getRecords(caller)

        assertEquals(listOf(LocalDate.of(2026, 9, 7)), result.quietTime.days)
        assertEquals(listOf(LocalDate.of(2026, 8, 31)), result.recitation.days)
        assertEquals(verseWeek, result.recitation.weekStart)
        assertEquals(84L, result.quietTime.totalDays)
        assertEquals(127L, result.recitation.totalDays)
    }

    // ─── Markability ───────────────────────────────────────────────────────────

    @Test
    fun `sunday is markable for the daily passage, without asking upstream`() {
        // The plan carries no Sunday entry, but the passages come from the sermon. The week
        // is seven pills, the same as 암송, and no upstream lookup can establish that.
        val result = service("2026-09-06T09:00:00Z").getRecords(caller)

        assertTrue(result.quietTime.todayMarkable)
        verify(client, never()).quietTime(any())
    }

    @Test
    fun `a day the plan skips is not markable`() {
        `when`(client.quietTime(tuesday)).thenReturn(null)

        assertFalse(service().getRecords(caller).quietTime.todayMarkable)
    }

    @Test
    fun `recitation is not markable while no verse has been chosen`() {
        `when`(verseService.currentWeeklyVerse()).thenReturn(null)
        `when`(client.quietTime(tuesday)).thenReturn(QuietTimeItem(book = 5, chapterStart = 3, verseStart = 1))

        assertFalse(service().getRecords(caller).recitation.todayMarkable)
    }

    @Test
    fun `an unreachable upstream still lets a member mark the day`() {
        `when`(client.quietTime(tuesday)).thenThrow(BibleApiUnavailableException("down"))

        // Refusing to record something the member actually did, because a third party is
        // down, is the worse of the two mistakes.
        assertTrue(service().getRecords(caller).quietTime.todayMarkable)
    }

    // ─── Writing ───────────────────────────────────────────────────────────────

    @Test
    fun `marking today writes one row and returns the refreshed block`() {
        `when`(verseService.currentWeeklyVerse()).thenReturn(weeklyVerse())
        `when`(recordRepository.insertIfAbsent(any(), eq(1L), eq(tuesday), eq("RECITATION"))).thenReturn(1)
        `when`(recordRepository.findMarksInRange(eq(1L), any(), any()))
            .thenReturn(listOf(VerseRecordMark(tuesday, VerseRecordKind.RECITATION)))
        `when`(recordRepository.countByKind(1L)).thenReturn(listOf(VerseRecordCount(VerseRecordKind.RECITATION, 1L)))

        val block = service().mark(caller, VerseRecordKind.RECITATION)

        assertTrue(block.todayMarked)
        assertEquals(1L, block.totalDays)
    }

    @Test
    fun `a second mark on the same day conflicts in the database, not in a race`() {
        `when`(verseService.currentWeeklyVerse()).thenReturn(weeklyVerse())
        `when`(recordRepository.insertIfAbsent(any(), eq(1L), eq(tuesday), eq("RECITATION"))).thenReturn(0)

        val exception = assertThrows<ResponseStatusException> { service().mark(caller, VerseRecordKind.RECITATION) }

        // 409, which the client treats as success — the member's intent already holds.
        assertEquals(409, exception.statusCode.value())
    }

    @Test
    fun `marking a sunday for the daily passage writes the row`() {
        val sunday = LocalDate.of(2026, 9, 6)
        `when`(recordRepository.insertIfAbsent(any(), eq(1L), eq(sunday), eq("QUIET_TIME"))).thenReturn(1)
        `when`(recordRepository.findMarksInRange(eq(1L), any(), any()))
            .thenReturn(listOf(VerseRecordMark(sunday, VerseRecordKind.QUIET_TIME)))

        val block = service("2026-09-06T09:00:00Z").mark(caller, VerseRecordKind.QUIET_TIME)

        assertTrue(block.todayMarked)
    }

    @Test
    fun `marking recitation without a chosen verse is refused`() {
        `when`(verseService.currentWeeklyVerse()).thenReturn(null)

        assertThrows<ResponseStatusException> { service().mark(caller, VerseRecordKind.RECITATION) }

        verify(recordRepository, never()).insertIfAbsent(any(), any(), any(), any())
    }
}
