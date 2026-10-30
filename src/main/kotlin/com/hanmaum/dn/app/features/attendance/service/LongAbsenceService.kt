package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.common.pii.PiiCryptoContext
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceActorDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceContactStatusRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceGroupDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceMemoRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceSummaryResponse
import com.hanmaum.dn.app.features.attendance.domain.AttendanceAbsenceFollowup
import com.hanmaum.dn.app.features.attendance.repository.AttendanceAbsenceFollowupRepository
import com.hanmaum.dn.app.features.attendance.repository.LongAbsenceCandidateView
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.domain.PostAssignmentAttendance
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 장기결석자 (HDN-375): members whose last Sunday check-in is at least three calendar months
 * back, and the contact status and memo of their current absence phase.
 *
 * The list is computed from attendance_logs on every read; only the follow-up is stored. Reads
 * never write. Writes lock the member first, then re-check that the phase the client saw is
 * still the current one, then compare the follow-up version.
 */
@Service
class LongAbsenceService(
    private val followupRepository: AttendanceAbsenceFollowupRepository,
    private val memberRepository: MemberRepository,
    private val policy: SundayAttendancePolicy,
    private val clock: Clock,
) {
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun list(): List<LongAbsenceResponse> {
        val today = LocalDate.now(clock)
        val candidates = candidates(today, memberId = null)
        if (candidates.isEmpty()) return emptyList()

        val memberIds = candidates.map { it.memberId }
        val members = memberRepository.findAllWithGroupByIdIn(memberIds).associateBy { it.id!! }
        val followups =
            followupRepository
                .findAllByMemberIds(memberIds)
                .associateBy { PhaseKey(it.member.id!!, it.lastSundayDate) }
        val views = candidates.associateWith { visible(followups[PhaseKey(it.memberId, it.lastSundayDate)], today) }
        val actors = resolveActors(views.values.mapNotNull { it.contactedBy })

        return candidates
            .map { toResponse(members.getValue(it.memberId), it, views.getValue(it), actors, today) }
            .sortedWith(compareBy<LongAbsenceResponse> { it.lastSundayDate }.thenBy { it.memberPublicId })
    }

    /** Counts for the home screen; reads no memo, name or actor. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun summary(): LongAbsenceSummaryResponse {
        val today = LocalDate.now(clock)
        val candidates = candidates(today, memberId = null)
        val contacted =
            if (candidates.isEmpty()) {
                emptySet()
            } else {
                followupRepository
                    .findStatesByMemberIds(candidates.map { it.memberId })
                    .filter { it.contacted && it.closedOn == null }
                    .map { PhaseKey(it.memberId, it.lastSundayDate) }
                    .toSet()
            }
        val uncontacted = candidates.filterNot { PhaseKey(it.memberId, it.lastSundayDate) in contacted }
        return LongAbsenceSummaryResponse(
            asOfDate = today,
            totalCount = candidates.size.toLong(),
            uncontactedCount = uncontacted.size.toLong(),
            atLeastSixMonthsCount =
                uncontacted
                    .count { !it.lastSundayDate.plusMonths(SundayAttendancePolicy.SUMMARY_URGENT_MONTHS).isAfter(today) }
                    .toLong(),
        )
    }

    @Transactional
    fun setContactStatus(
        memberPublicId: UUID,
        request: LongAbsenceContactStatusRequest,
        actorSubject: String,
    ): LongAbsenceResponse =
        write(memberPublicId, request.lastSundayDate!!, request.version!!) { followup, now ->
            followup.markContacted(request.contacted!!, actorSubject, now)
            request.memoUpdate?.let { followup.memo = normalizeMemo(it.value) }
        }

    @Transactional
    fun setMemo(
        memberPublicId: UUID,
        request: LongAbsenceMemoRequest,
    ): LongAbsenceResponse =
        write(memberPublicId, request.lastSundayDate!!, request.version!!) { followup, _ ->
            followup.memo = normalizeMemo(request.memo)
        }

    /**
     * Status and memo change in one transaction or not at all. The member lock serialises this
     * with the check-in writers and with a first insert of the same phase.
     */
    private fun write(
        memberPublicId: UUID,
        lastSundayDate: LocalDate,
        version: Long,
        change: (AttendanceAbsenceFollowup, Instant) -> Unit,
    ): LongAbsenceResponse {
        val member =
            memberRepository
                .findForUpdateByPublicIdAndDeletedAtIsNull(memberPublicId)
                .orElseThrow { LongAbsenceException.memberNotFound() }
        val today = LocalDate.now(clock)
        val candidate =
            candidates(today, member.id)
                .firstOrNull()
                ?.takeIf { it.lastSundayDate == lastSundayDate }
                ?: throw LongAbsenceException.notCurrent()

        val stored = followupRepository.findPhase(member.id!!, lastSundayDate)?.takeUnless { purgeIfDue(it, today) }
        if (version != visible(stored, today).version) throw LongAbsenceException.versionConflict()

        val followup = stored ?: AttendanceAbsenceFollowup(member = member, lastSundayDate = lastSundayDate)
        if (followup.closedOn != null) followup.reopen()
        change(followup, clock.instant())
        if (stored == null) {
            // A request that leaves the default state as it is records nothing.
            if (followup.contacted || followup.memo != null) followupRepository.saveAndFlush(followup)
        } else {
            followupRepository.flush()
        }

        val view = visible(followup.takeIf { it.id != null }, today)
        return toResponse(member, candidate, view, resolveActors(listOfNotNull(view.contactedBy)), today)
    }

    /** Deletes a closed phase whose retention ran out but which the daily job has not removed yet. */
    private fun purgeIfDue(
        followup: AttendanceAbsenceFollowup,
        today: LocalDate,
    ): Boolean {
        val closedOn = followup.closedOn ?: return false
        if (!LongAbsenceRetentionService.isDue(closedOn, today)) return false
        followupRepository.delete(followup)
        followupRepository.flush()
        return true
    }

    private fun candidates(
        today: LocalDate,
        memberId: Long?,
    ): List<LongAbsenceCandidateView> =
        followupRepository
            .findCandidates(
                today = today,
                upperBound = SundayAttendancePolicy.candidateUpperBound(today),
                sundayServiceIds = policy.sundayServiceIdsParameter,
                memberId = memberId,
            ).filter { SundayAttendancePolicy.isListed(it.lastSundayDate, today) }

    /**
     * What a client may see of a stored follow-up. A closed row only exists here if a log
     * correction made its anchor current again: its content belongs to the ended phase and is
     * never shown, but its version still guards the next write. Past retention it is as good
     * as deleted.
     */
    private fun visible(
        followup: AttendanceAbsenceFollowup?,
        today: LocalDate,
    ): FollowupView {
        if (followup == null) return FollowupView.EMPTY
        val closedOn = followup.closedOn
        return when {
            closedOn != null && LongAbsenceRetentionService.isDue(closedOn, today) -> FollowupView.EMPTY
            closedOn != null -> FollowupView.EMPTY.copy(version = followup.version + 1)
            else ->
                FollowupView(
                    contacted = followup.contacted,
                    contactedBy = followup.contactedBy,
                    contactedAt = followup.contactedAt,
                    memo = followup.memo,
                    // The first insert stores JPA version 0; the wire counts it as 1 so that 0
                    // always means "nothing recorded".
                    version = followup.version + 1,
                )
        }
    }

    /** Actor subjects to their member profile, via the lookup hash; admin-only accounts have none. */
    private fun resolveActors(subjects: Collection<String>): Map<String, Member> {
        val bySubjectHash =
            subjects
                .distinct()
                .mapNotNull { subject ->
                    PiiCryptoContext.lookupHash(subject)?.let { it to subject }
                }.toMap()
        if (bySubjectHash.isEmpty()) return emptyMap()
        return memberRepository
            .findAllByKeycloakLookupHashInAndDeletedAtIsNull(bySubjectHash.keys)
            .mapNotNull { member -> bySubjectHash[member.keycloakLookupHash]?.let { it to member } }
            .toMap()
    }

    private fun toResponse(
        member: Member,
        candidate: LongAbsenceCandidateView,
        view: FollowupView,
        actors: Map<String, Member>,
        today: LocalDate,
    ): LongAbsenceResponse {
        val lastSundayDate = candidate.lastSundayDate
        return LongAbsenceResponse(
            memberPublicId = member.publicId,
            fullName = member.getFullName(),
            discriminator = member.discriminator,
            group =
                member.group?.takeIf { it.deletedAt == null }?.let {
                    LongAbsenceGroupDto(publicId = it.publicId, name = it.name, division = it.division)
                },
            lastSundayDate = lastSundayDate,
            absentSince = lastSundayDate.plusDays(1),
            absenceDays = SundayAttendancePolicy.absenceDays(lastSundayDate, today),
            absenceMonths = SundayAttendancePolicy.absenceMonths(lastSundayDate, today),
            postAssignmentAttendance =
                candidate.postAssignmentAttendance?.let { stored ->
                    PostAssignmentAttendance.entries.firstOrNull { it.name == stored }
                },
            contacted = view.contacted,
            contactedBy = view.contactedBy,
            contactedByMember =
                view.contactedBy?.let(actors::get)?.let {
                    LongAbsenceActorDto(publicId = it.publicId, fullName = it.getFullName())
                },
            contactedAt = view.contactedAt,
            memo = view.memo,
            version = view.version,
        )
    }

    private data class PhaseKey(
        val memberId: Long,
        val lastSundayDate: LocalDate,
    )

    private data class FollowupView(
        val contacted: Boolean,
        val contactedBy: String?,
        val contactedAt: Instant?,
        val memo: String?,
        val version: Long,
    ) {
        override fun toString(): String = "FollowupView(contacted=$contacted, version=$version)"

        companion object {
            val EMPTY = FollowupView(contacted = false, contactedBy = null, contactedAt = null, memo = null, version = 0)
        }
    }

    companion object {
        /** Blank means no memo; anything else is kept exactly as written. */
        fun normalizeMemo(value: String?): String? = value?.takeUnless { it.isBlank() }
    }
}
