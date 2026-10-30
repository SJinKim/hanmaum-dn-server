package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.common.pii.ABSENCE_CHANGE_ACTOR_CONTEXT
import com.hanmaum.dn.app.common.pii.ABSENCE_CHANGE_MEMO_CONTEXT
import com.hanmaum.dn.app.common.pii.ABSENCE_FOLLOWUP_CONTACTED_BY_CONTEXT
import com.hanmaum.dn.app.common.pii.ABSENCE_FOLLOWUP_MEMO_CONTEXT
import com.hanmaum.dn.app.common.pii.ABSENCE_FOLLOWUP_RETURNED_BY_CONTEXT
import com.hanmaum.dn.app.common.pii.PiiCryptoContext
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceChangeDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceChangePage
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceHistoryItem
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceHistoryPage
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsencePhaseResponse
import com.hanmaum.dn.app.features.attendance.domain.AbsenceChangeKind
import com.hanmaum.dn.app.features.attendance.domain.AbsenceChangeOrigin
import com.hanmaum.dn.app.features.attendance.domain.AbsencePhaseEndReason
import com.hanmaum.dn.app.features.attendance.repository.AbsencePhaseRow
import com.hanmaum.dn.app.features.attendance.repository.AttendanceAbsenceChangeRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceAbsenceFollowupRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Read side of the 장기결석자 history (HDN-379): closed phases, one stored phase and its contact
 * and memo versions, within the retention of decision #380. Nothing here writes or purges.
 *
 * Pages are cut in SQL; only the rows of the page are decrypted and enriched, and a memo whose
 * 90 days ran out is filtered out before it is decrypted. Count and slice share one
 * REPEATABLE_READ transaction.
 */
@Service
class LongAbsenceHistoryService(
    private val followupRepository: AttendanceAbsenceFollowupRepository,
    private val changeRepository: AttendanceAbsenceChangeRepository,
    private val memberRepository: MemberRepository,
    private val phases: LongAbsencePhases,
    private val clock: Clock,
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun history(
        memberPublicId: UUID?,
        page: Int,
        size: Int,
    ): LongAbsenceHistoryPage {
        val today = LocalDate.now(clock)
        val memberId = memberPublicId?.let { requireMember(it).id!! }
        val phaseCutoff = LongAbsenceRetentionService.phaseCutoff(today)
        val total = followupRepository.countHistory(phaseCutoff, memberId)
        val rows = followupRepository.findHistory(phaseCutoff, memberId, size, page.toLong() * size)

        val members = memberRepository.findAllById(rows.map { it.memberId }.distinct()).associateBy { it.id!! }
        val decrypted = rows.map { it.decrypt() }
        val actors = phases.resolveActors(decrypted.flatMap { listOfNotNull(it.contactedBy, it.returnedBy) })
        val items =
            decrypted.map { phase ->
                val member = members.getValue(phase.row.memberId)
                LongAbsenceHistoryItem(
                    phasePublicId = phase.row.phasePublicId,
                    memberPublicId = phase.row.memberPublicId,
                    fullName = member.getFullName(),
                    discriminator = member.discriminator,
                    lastSundayDate = phase.row.lastSundayDate,
                    absenceAnchorDate = phase.row.absenceAnchorDate,
                    absentSince = phase.row.absenceAnchorDate.plusDays(1),
                    endedOn = phase.row.closedOn!!,
                    endReason = phase.endReason!!,
                    contacted = phase.row.contacted,
                    contactedBy = phase.contactedBy,
                    contactedByMember = phase.contactedBy?.let(actors::get),
                    contactedAt = phase.row.contactedAt,
                    returnedAt = phase.returnedAt,
                    returnedBy = phase.returnedBy,
                    returnedByMember = phase.returnedBy?.let(actors::get),
                )
            }
        return LongAbsenceHistoryPage(items, page, size, total, totalPages(total, size))
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun phase(
        memberPublicId: UUID,
        phasePublicId: UUID,
    ): LongAbsencePhaseResponse {
        val today = LocalDate.now(clock)
        val member = requireMember(memberPublicId)
        val phase = requirePhase(member, phasePublicId, today).decrypt()
        val current = phases.current(member.id!!, today)
        val actors = phases.resolveActors(listOfNotNull(phase.contactedBy, phase.returnedBy))
        return LongAbsencePhaseResponse(
            phasePublicId = phase.row.phasePublicId,
            memberPublicId = member.publicId,
            fullName = member.getFullName(),
            discriminator = member.discriminator,
            lastSundayDate = phase.row.lastSundayDate,
            absenceAnchorDate = phase.row.absenceAnchorDate,
            absentSince = phase.row.absenceAnchorDate.plusDays(1),
            endedOn = phase.row.closedOn,
            endReason = phase.endReason,
            contacted = phase.row.contacted,
            contactedBy = phase.contactedBy,
            contactedByMember = phase.contactedBy?.let(actors::get),
            contactedAt = phase.row.contactedAt,
            returnedAt = phase.returnedAt,
            returnedBy = phase.returnedBy,
            returnedByMember = phase.returnedBy?.let(actors::get),
            memo = PiiCryptoContext.decrypt(phase.row.memo, ABSENCE_FOLLOWUP_MEMO_CONTEXT),
            isCurrent =
                phase.row.closedOn == null &&
                    current != null &&
                    current.lastSundayDate == phase.row.lastSundayDate &&
                    current.absenceAnchorDate == phase.row.absenceAnchorDate,
        )
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun changes(
        memberPublicId: UUID,
        phasePublicId: UUID,
        page: Int,
        size: Int,
    ): LongAbsenceChangePage {
        val today = LocalDate.now(clock)
        val phase = requirePhase(requireMember(memberPublicId), phasePublicId, today)
        val memoCutoff = LongAbsenceRetentionService.memoCutoff(today)
        val total = changeRepository.countChanges(phase.id, memoCutoff)
        val rows = changeRepository.findChanges(phase.id, memoCutoff, size, page.toLong() * size)

        val subjects = rows.map { it.publicId to PiiCryptoContext.decrypt(it.actorSubject, ABSENCE_CHANGE_ACTOR_CONTEXT) }.toMap()
        val actors = phases.resolveActors(subjects.values.filterNotNull())
        val items =
            rows.map { row ->
                val subject = subjects[row.publicId]
                LongAbsenceChangeDto(
                    publicId = row.publicId,
                    revision = row.revision,
                    kind = AbsenceChangeKind.valueOf(row.kind),
                    origin = AbsenceChangeOrigin.valueOf(row.origin),
                    occurredAt = row.occurredAt,
                    actorSubject = subject,
                    actorMember = subject?.let(actors::get),
                    contacted = row.contacted,
                    memo = PiiCryptoContext.decrypt(row.memo, ABSENCE_CHANGE_MEMO_CONTEXT),
                )
            }
        return LongAbsenceChangePage(items, page, size, total, totalPages(total, size))
    }

    private fun requireMember(publicId: UUID): Member =
        memberRepository
            .findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow { LongAbsenceException.memberNotFound() }

    /** Unknown, another member's or past its ten years all read the same, so no phase id is confirmed. */
    private fun requirePhase(
        member: Member,
        phasePublicId: UUID,
        today: LocalDate,
    ): AbsencePhaseRow =
        followupRepository.findPhase(
            memberId = member.id!!,
            phasePublicId = phasePublicId,
            phaseCutoff = LongAbsenceRetentionService.phaseCutoff(today),
            memoCutoff = LongAbsenceRetentionService.memoCutoff(today),
        ) ?: throw LongAbsenceException.phaseNotFound()

    private fun AbsencePhaseRow.decrypt(): DecryptedPhase {
        val endReason = endReason?.let(AbsencePhaseEndReason::valueOf)
        val manual = endReason == AbsencePhaseEndReason.MANUAL_RETURN
        return DecryptedPhase(
            row = this,
            endReason = endReason,
            contactedBy = PiiCryptoContext.decrypt(contactedBy, ABSENCE_FOLLOWUP_CONTACTED_BY_CONTEXT),
            // A check-in return has a visit date but nobody who confirmed it.
            returnedBy = if (manual) PiiCryptoContext.decrypt(returnedBy, ABSENCE_FOLLOWUP_RETURNED_BY_CONTEXT) else null,
            returnedAt = if (manual) endRecordedAt else null,
        )
    }

    private class DecryptedPhase(
        val row: AbsencePhaseRow,
        val endReason: AbsencePhaseEndReason?,
        val contactedBy: String?,
        val returnedBy: String?,
        val returnedAt: Instant?,
    ) {
        override fun toString(): String = "DecryptedPhase(phasePublicId=${row.phasePublicId}, endReason=$endReason)"
    }

    private fun totalPages(
        total: Long,
        size: Int,
    ): Int = ((total + size - 1) / size).toInt()

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
