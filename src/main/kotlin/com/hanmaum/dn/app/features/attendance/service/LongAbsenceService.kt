package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceActorDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceContactStatusRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceGroupDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceMemoRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceReturnRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceReturnResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceSummaryResponse
import com.hanmaum.dn.app.features.attendance.domain.AbsencePhaseEndReason
import com.hanmaum.dn.app.features.attendance.domain.AttendanceAbsenceChange
import com.hanmaum.dn.app.features.attendance.domain.AttendanceAbsenceFollowup
import com.hanmaum.dn.app.features.attendance.repository.AttendanceAbsenceChangeRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceAbsenceFollowupRepository
import com.hanmaum.dn.app.features.attendance.repository.LongAbsenceCandidateView
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.domain.PostAssignmentAttendance
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * 장기결석자 (HDN-375, HDN-379): members whose absence anchor (the later of the last Sunday
 * check-in and a manual return) is at least three calendar months back, and the contact status
 * and memo of their current absence phase.
 *
 * The list is computed from attendance_logs on every read; only phases with something recorded
 * are stored. Reads never write. Writes lock the member first, then re-check that the phase the
 * client saw is still the current one, then compare the member's conflict counter. Every real
 * change raises that counter and records its new value as a history entry of the phase.
 */
@Service
class LongAbsenceService(
    private val followupRepository: AttendanceAbsenceFollowupRepository,
    private val changeRepository: AttendanceAbsenceChangeRepository,
    private val memberRepository: MemberRepository,
    private val phases: LongAbsencePhases,
    private val clock: Clock,
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun list(): List<LongAbsenceResponse> {
        val today = LocalDate.now(clock)
        val candidates = phases.candidates(today, memberId = null)
        if (candidates.isEmpty()) return emptyList()

        val memberIds = candidates.map { it.memberId }
        val members = memberRepository.findAllWithGroupByIdIn(memberIds).associateBy { it.id!! }
        val stored =
            followupRepository
                .findOpenByMemberIds(memberIds)
                .groupBy { PhaseKey(it.member.id!!, it.lastSundayDate, it.absenceAnchorDate) }
                .mapValues { (_, rows) -> rows.maxBy { it.phaseGeneration } }
        val current = candidates.associateWith { stored[it.key()] }
        val actors = phases.resolveActors(current.values.mapNotNull { it?.contactedBy })

        return candidates
            .map { toResponse(members.getValue(it.memberId), it, current[it], actors, today) }
            .sortedWith(compareBy<LongAbsenceResponse> { it.absenceAnchorDate }.thenBy { it.memberPublicId })
    }

    /** Counts for the home screen; reads no memo, name or actor. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun summary(): LongAbsenceSummaryResponse {
        val today = LocalDate.now(clock)
        val candidates = phases.candidates(today, memberId = null)
        val contacted =
            if (candidates.isEmpty()) {
                emptySet()
            } else {
                followupRepository
                    .findOpenStatesByMemberIds(candidates.map { it.memberId })
                    .filter { it.contacted }
                    .map { PhaseKey(it.memberId, it.lastSundayDate, it.absenceAnchorDate) }
                    .toSet()
            }
        val uncontacted = candidates.filterNot { it.key() in contacted }
        return LongAbsenceSummaryResponse(
            asOfDate = today,
            totalCount = candidates.size.toLong(),
            uncontactedCount = uncontacted.size.toLong(),
            atLeastSixMonthsCount =
                uncontacted
                    .count { !it.absenceAnchorDate.plusMonths(SundayAttendancePolicy.SUMMARY_URGENT_MONTHS).isAfter(today) }
                    .toLong(),
        )
    }

    @Transactional
    fun setContactStatus(
        memberPublicId: UUID,
        request: LongAbsenceContactStatusRequest,
        actorSubject: String,
    ): LongAbsenceResponse =
        write(memberPublicId, request.lastSundayDate!!, request.version!!, actorSubject) {
            contacted = request.contacted!!
            request.memoUpdate?.let { memo = Change(normalizeMemo(it.value)) }
        }

    @Transactional
    fun setMemo(
        memberPublicId: UUID,
        request: LongAbsenceMemoRequest,
        actorSubject: String,
    ): LongAbsenceResponse =
        write(memberPublicId, request.lastSundayDate!!, request.version!!, actorSubject) {
            memo = Change(normalizeMemo(request.memo))
        }

    /**
     * Ends the current phase without a check-in: stores it if nothing was recorded yet, closes it
     * with today's Berlin date, the actor and the server time, and lets the next absence count
     * from today. No attendance log is written and no new phase is opened; a member who stays away
     * is listed again three months later with an empty follow-up.
     */
    @Transactional
    fun markReturned(
        memberPublicId: UUID,
        request: LongAbsenceReturnRequest,
        actorSubject: String,
    ): LongAbsenceReturnResponse {
        val (member, candidate, stored) = lockCurrent(memberPublicId, request.lastSundayDate!!, request.version!!)
        val today = LocalDate.now(clock)
        val now = clock.instant()

        val revision = member.nextAbsenceRevision()
        val phase = stored ?: newPhase(member, candidate, revision)
        phase.closeByManualReturn(today, actorSubject, now)
        member.absenceManualReturnOn = today
        followupRepository.saveAndFlush(phase)

        return LongAbsenceReturnResponse(
            memberPublicId = member.publicId,
            phasePublicId = phase.publicId,
            endedOn = today,
            endReason = AbsencePhaseEndReason.MANUAL_RETURN,
            returnedAt = now,
            returnedBy = actorSubject,
            returnedByMember = phases.resolveActors(listOf(actorSubject))[actorSubject],
            version = member.absenceRevision,
        )
    }

    /**
     * Status and memo change in one transaction or not at all, sharing one revision and time.
     * Only what actually changes is written and recorded; a request that changes nothing records
     * nothing and keeps the counter. Clearing the memo deletes every memo version of the phase
     * and leaves no entry behind (#380).
     */
    private fun write(
        memberPublicId: UUID,
        lastSundayDate: LocalDate,
        version: Long,
        actorSubject: String,
        request: WriteRequest.() -> Unit,
    ): LongAbsenceResponse {
        val (member, candidate, stored) = lockCurrent(memberPublicId, lastSundayDate, version)
        val wanted = WriteRequest().apply(request)
        val contactChange = wanted.contacted?.takeIf { it != (stored?.contacted ?: false) }
        val memoChange = wanted.memo?.takeIf { it.value != stored?.memo }

        val today = LocalDate.now(clock)
        if (contactChange == null && memoChange == null) {
            return toResponse(member, candidate, stored, phases.resolveActors(listOfNotNull(stored?.contactedBy)), today)
        }

        val now = clock.instant()
        val revision = member.nextAbsenceRevision()
        val phase = stored ?: newPhase(member, candidate, revision)
        contactChange?.let { phase.markContacted(it, actorSubject, now) }
        memoChange?.let { phase.memo = it.value }
        followupRepository.save(phase)

        contactChange?.let { changeRepository.save(AttendanceAbsenceChange.contact(phase, revision, it, actorSubject, now)) }
        memoChange?.value.let { memo ->
            when {
                memoChange == null -> Unit
                // Only a stored phase can have a memo to clear.
                memo == null -> changeRepository.deleteMemoVersions(phase.id!!)
                else -> changeRepository.save(AttendanceAbsenceChange.memo(phase, revision, memo, actorSubject, now))
            }
        }
        followupRepository.flush()

        return toResponse(member, candidate, phase, phases.resolveActors(listOfNotNull(phase.contactedBy)), today)
    }

    /**
     * Locks the member, then checks that [lastSundayDate] still names the listed phase and that
     * [version] is the current conflict counter, in that order.
     */
    private fun lockCurrent(
        memberPublicId: UUID,
        lastSundayDate: LocalDate,
        version: Long,
    ): LockedPhase {
        val member =
            memberRepository
                .findForUpdateByPublicIdAndDeletedAtIsNull(memberPublicId)
                .orElseThrow { LongAbsenceException.memberNotFound() }
        val candidate =
            phases
                .current(member.id!!, LocalDate.now(clock))
                ?.takeIf { it.lastSundayDate == lastSundayDate }
                ?: throw LongAbsenceException.notCurrent()
        if (version != member.absenceRevision) throw LongAbsenceException.versionConflict()
        val stored =
            followupRepository
                .findOpenPhases(member.id!!, candidate.lastSundayDate, candidate.absenceAnchorDate)
                .firstOrNull()
        return LockedPhase(member, candidate, stored)
    }

    private fun newPhase(
        member: Member,
        candidate: LongAbsenceCandidateView,
        generation: Long,
    ) = AttendanceAbsenceFollowup(
        member = member,
        lastSundayDate = candidate.lastSundayDate,
        absenceAnchorDate = candidate.absenceAnchorDate,
        phaseGeneration = generation,
    )

    private fun toResponse(
        member: Member,
        candidate: LongAbsenceCandidateView,
        phase: AttendanceAbsenceFollowup?,
        actors: Map<String, LongAbsenceActorDto>,
        today: LocalDate,
    ): LongAbsenceResponse {
        val anchor = candidate.absenceAnchorDate
        return LongAbsenceResponse(
            memberPublicId = member.publicId,
            phasePublicId = phase?.publicId,
            fullName = member.getFullName(),
            discriminator = member.discriminator,
            group =
                member.group?.takeIf { it.deletedAt == null }?.let {
                    LongAbsenceGroupDto(publicId = it.publicId, name = it.name, division = it.division)
                },
            lastSundayDate = candidate.lastSundayDate,
            absenceAnchorDate = anchor,
            absentSince = anchor.plusDays(1),
            absenceDays = SundayAttendancePolicy.absenceDays(anchor, today),
            absenceMonths = SundayAttendancePolicy.absenceMonths(anchor, today),
            postAssignmentAttendance =
                candidate.postAssignmentAttendance?.let { stored ->
                    PostAssignmentAttendance.entries.firstOrNull { it.name == stored }
                },
            contacted = phase?.contacted ?: false,
            contactedBy = phase?.contactedBy,
            contactedByMember = phase?.contactedBy?.let(actors::get),
            contactedAt = phase?.contactedAt,
            memo = phase?.memo,
            version = member.absenceRevision,
        )
    }

    private fun LongAbsenceCandidateView.key() = PhaseKey(memberId, lastSundayDate, absenceAnchorDate)

    private data class PhaseKey(
        val memberId: Long,
        val lastSundayDate: LocalDate,
        val absenceAnchorDate: LocalDate,
    )

    private data class LockedPhase(
        val member: Member,
        val candidate: LongAbsenceCandidateView,
        val stored: AttendanceAbsenceFollowup?,
    )

    /** What a write asks for; null leaves the value alone. */
    private class WriteRequest {
        var contacted: Boolean? = null
        var memo: Change? = null
    }

    /** A requested memo value, null meaning "clear". */
    private class Change(
        val value: String?,
    ) {
        override fun toString(): String = "Change(<redacted>)"
    }

    companion object {
        /** Blank means no memo; anything else is kept exactly as written. */
        fun normalizeMemo(value: String?): String? = value?.takeUnless { it.isBlank() }
    }
}
