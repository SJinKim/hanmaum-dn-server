package com.hanmaum.dn.app.features.verses.service

import com.hanmaum.dn.app.common.domainvalue.VerseRecordKind
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.verses.api.v1.dto.VerseRecordBlock
import com.hanmaum.dn.app.features.verses.api.v1.dto.VerseRecordsResponse
import com.hanmaum.dn.app.features.verses.client.BibleApiClient
import com.hanmaum.dn.app.features.verses.client.BibleApiUnavailableException
import com.hanmaum.dn.app.features.verses.repository.VerseRecordRepository
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

@Service
class VerseRecordService(
    private val recordRepository: VerseRecordRepository,
    private val memberRepository: MemberRepository,
    private val verseService: VerseService,
    private val client: BibleApiClient,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(VerseRecordService::class.java)

    /** Both streaks in one read, so Home does not load twice for one screen. */
    @Transactional(readOnly = true)
    fun getRecords(keycloakSubject: String): VerseRecordsResponse {
        val member = resolveMember(keycloakSubject)
        return VerseRecordsResponse(
            quietTime = block(member, VerseRecordKind.QUIET_TIME),
            recitation = block(member, VerseRecordKind.RECITATION),
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
        keycloakSubject: String,
        kind: VerseRecordKind,
    ): VerseRecordBlock {
        val member = resolveMember(keycloakSubject)
        val today = LocalDate.now(clock)

        if (!isMarkableToday(kind, today)) {
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
        return block(member, kind)
    }

    private fun block(
        member: Member,
        kind: VerseRecordKind,
    ): VerseRecordBlock {
        val today = LocalDate.now(clock)
        val weekStart = weekStartFor(kind, today)
        val days =
            recordRepository.findDatesInRange(
                memberId = member.id!!,
                kind = kind.name,
                from = weekStart,
                to = weekStart.plusDays(6),
            )
        return VerseRecordBlock(
            weekStart = weekStart,
            days = days,
            todayMarked = days.contains(today),
            todayMarkable = isMarkableToday(kind, today),
            totalDays = recordRepository.countForMember(member.id!!, kind.name),
        )
    }

    /**
     * 암송 follows the week of the chosen verse; 오늘의 말씀 follows the calendar week.
     * Both start on Sunday, so the seven pills line up either way.
     */
    private fun weekStartFor(
        kind: VerseRecordKind,
        today: LocalDate,
    ): LocalDate =
        when (kind) {
            VerseRecordKind.RECITATION ->
                verseService.currentWeeklyVerse()?.weekStart ?: verseService.weekStartOf(today)
            VerseRecordKind.QUIET_TIME -> verseService.weekStartOf(today)
        }

    /**
     * Whether today can be marked, computed here so the client never has to derive it.
     *
     * 암송 needs a verse to recite — without a chosen one the card has nothing to offer.
     *
     * 오늘의 말씀 is markable on every day there is something to read, and Sunday is one of
     * them. The reading plan carries no Sunday entry, but that is not an empty day: the
     * passages come from the sermon, so a member who goes to church and reads along has
     * done the same thing as on any other day. Treating Sunday as unmarkable made a full
     * week 6/7 by construction and quietly told those members their Sunday did not count.
     * The week is seven pills, the same as 암송.
     *
     * The other days need the upstream, because a gap in the plan is a real absence.
     *
     * When the upstream cannot be reached the answer is *yes*. Refusing to record something
     * a member actually did, because a third party is down, is the worse of the two
     * mistakes: the member loses a day they earned, and the row we might wrongly accept
     * costs nothing but a mark on a day whose plan we could not confirm.
     */
    private fun isMarkableToday(
        kind: VerseRecordKind,
        today: LocalDate,
    ): Boolean =
        when (kind) {
            VerseRecordKind.RECITATION -> verseService.currentWeeklyVerse() != null
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

    private fun resolveMember(keycloakSubject: String): Member =
        memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)
            ?: throw EntityNotFoundException("Member not found for subject: $keycloakSubject")
}
