package com.hanmaum.dn.app.features.verses.service

import com.hanmaum.dn.app.common.domainvalue.VerseRecordKind
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.members.service.MemberPrincipal
import com.hanmaum.dn.app.features.verses.api.v1.dto.VerseRecordBlock
import com.hanmaum.dn.app.features.verses.api.v1.dto.VerseRecordsResponse
import com.hanmaum.dn.app.features.verses.client.BibleApiClient
import com.hanmaum.dn.app.features.verses.client.BibleApiUnavailableException
import com.hanmaum.dn.app.features.verses.domain.WeeklyVerse
import com.hanmaum.dn.app.features.verses.repository.VerseRecordMark
import com.hanmaum.dn.app.features.verses.repository.VerseRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

@Service
class VerseRecordService(
    private val recordRepository: VerseRecordRepository,
    private val currentMemberResolver: CurrentMemberResolver,
    private val verseService: VerseService,
    private val client: BibleApiClient,
    private val clock: java.time.Clock,
) {
    private val log = LoggerFactory.getLogger(VerseRecordService::class.java)

    /**
     * Both streaks in one read, so Home does not load twice for one screen.
     *
     * Four queries: the member, the week's verse, the marks, the totals. It was seven, one
     * of which resolved the weekly verse a second time inside the same call.
     *
     * Not read-only, despite being a GET: resolving the caller may adopt a legacy member row.
     * A read-only transaction would leave that write unflushed and the adoption would
     * silently not happen. That a read path can write at all is the wart named in HDN-161 —
     * the annotation says so out loud rather than hiding it.
     */
    @Transactional
    fun getRecords(caller: MemberPrincipal): VerseRecordsResponse {
        val member = resolveMember(caller)
        val context = loadContext(member)
        return VerseRecordsResponse(
            quietTime = context.block(VerseRecordKind.QUIET_TIME),
            recitation = context.block(VerseRecordKind.RECITATION),
        )
    }

    /**
     * Marks today for one kind.
     *
     * Returns the refreshed block. A day already marked conflicts in the database rather
     * than in a read-then-write race, and surfaces as 409 — which the client treats as
     * success, because the member's intent ("this is done") already holds.
     */
    @Transactional
    fun mark(
        caller: MemberPrincipal,
        kind: VerseRecordKind,
    ): VerseRecordBlock {
        val member = resolveMember(caller)
        val today = LocalDate.now(clock)
        val weeklyVerse = verseService.currentWeeklyVerse()

        if (!isMarkableToday(kind, today, weeklyVerse)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "오늘은 기록할 수 없습니다.")
        }

        val inserted =
            recordRepository.insertIfAbsent(
                publicId = UUID.randomUUID(),
                memberId = member.id!!,
                recordDate = today,
                kind = kind.name,
            )
        if (inserted == 0) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "오늘은 이미 기록했습니다.")
        }

        log.info("Verse record written kind={} memberId={}", kind, member.id)
        return loadContext(member, weeklyVerse).block(kind)
    }

    /**
     * Everything both blocks need, read once.
     *
     * The two streaks can sit on different weeks — recitation follows the chosen verse — so
     * the marks are fetched across the span covering both and split by kind here.
     */
    private fun loadContext(
        member: Member,
        preloadedWeeklyVerse: WeeklyVerse? = null,
    ): BlockContext {
        val today = LocalDate.now(clock)
        val weeklyVerse = preloadedWeeklyVerse ?: verseService.currentWeeklyVerse()
        val quietTimeWeek = verseService.weekStartOf(today)
        val recitationWeek = weeklyVerse?.weekStart ?: quietTimeWeek
        val marks =
            recordRepository.findMarksInRange(
                memberId = member.id!!,
                from = minOf(quietTimeWeek, recitationWeek),
                to = maxOf(quietTimeWeek, recitationWeek).plusDays(6),
            )
        val totals = recordRepository.countByKind(member.id!!).associate { it.kind to it.total }
        return BlockContext(today, weeklyVerse, quietTimeWeek, recitationWeek, marks, totals)
    }

    private inner class BlockContext(
        val today: LocalDate,
        val weeklyVerse: WeeklyVerse?,
        val quietTimeWeek: LocalDate,
        val recitationWeek: LocalDate,
        val marks: List<VerseRecordMark>,
        val totals: Map<VerseRecordKind, Long>,
    ) {
        fun block(kind: VerseRecordKind): VerseRecordBlock {
            val weekStart = if (kind == VerseRecordKind.RECITATION) recitationWeek else quietTimeWeek
            val weekEnd = weekStart.plusDays(6)
            val days =
                marks
                    .filter { it.kind == kind && !it.recordDate.isBefore(weekStart) && !it.recordDate.isAfter(weekEnd) }
                    .map { it.recordDate }
            return VerseRecordBlock(
                weekStart = weekStart,
                days = days,
                todayMarked = days.contains(today),
                todayMarkable = isMarkableToday(kind, today, weeklyVerse),
                totalDays = totals[kind] ?: 0L,
            )
        }
    }

    /**
     * Whether today can be marked, computed here so the client never has to derive it.
     *
     * 암송 needs a verse to recite — without a chosen one the card has nothing to offer.
     *
     * 오늘의 말씀 is markable on every day there is something to read, and Sunday is one of
     * them. The reading plan carries no Sunday entry, but that is not an empty day: the
     * passages come from the sermon, so a member who goes to church and reads along has done
     * the same thing as on any other day. The week is seven pills, the same as 암송.
     *
     * The other days need the upstream, because a gap in the plan is a real absence. That
     * lookup is cached per date inside the client, so a congregation opening Home on a
     * Sunday morning costs one call rather than one per member.
     *
     * When the upstream cannot be reached the answer is *yes*. Refusing to record something
     * a member actually did, because a third party is down, is the worse of the two
     * mistakes: the member loses a day they earned, and the row we might wrongly accept
     * costs nothing but a mark on a day whose plan we could not confirm.
     */
    private fun isMarkableToday(
        kind: VerseRecordKind,
        today: LocalDate,
        weeklyVerse: WeeklyVerse?,
    ): Boolean =
        when (kind) {
            VerseRecordKind.RECITATION -> weeklyVerse != null
            VerseRecordKind.QUIET_TIME -> {
                if (today.dayOfWeek == DayOfWeek.SUNDAY) {
                    // The sermon is the passage. No upstream lookup can tell us that.
                    true
                } else {
                    try {
                        client.quietTime(today) != null
                    } catch (e: BibleApiUnavailableException) {
                        log.warn("Quiet-time availability unknown, allowing the mark: {}", e.message)
                        true
                    }
                }
            }
        }

    // The linking variant, matching /members/me. A legacy account that reaches the member
    // area through the profile endpoint must not then fail here — which is exactly what a
    // bare lookup did, and why the streak bars never appeared in 0.8.0.
    private fun resolveMember(caller: MemberPrincipal): Member =
        currentMemberResolver.resolveAndLink(caller.subject, caller.email, caller.emailVerified)
}
