package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.common.pii.PiiCryptoContext
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceActorDto
import com.hanmaum.dn.app.features.attendance.repository.AttendanceAbsenceFollowupRepository
import com.hanmaum.dn.app.features.attendance.repository.LongAbsenceCandidateView
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * What the 장기결석자 services share (HDN-375, HDN-379): who is listed today, with which anchor,
 * and who the stored actor subjects are.
 */
@Component
class LongAbsencePhases(
    private val followupRepository: AttendanceAbsenceFollowupRepository,
    private val memberRepository: MemberRepository,
    private val policy: SundayAttendancePolicy,
) {
    /** Everyone listed today ([memberId] null) or the one member, if listed. */
    fun candidates(
        today: LocalDate,
        memberId: Long?,
    ): List<LongAbsenceCandidateView> =
        followupRepository
            .findCandidates(
                today = today,
                upperBound = SundayAttendancePolicy.candidateUpperBound(today),
                sundayServiceIds = policy.sundayServiceIdsParameter,
                memberId = memberId,
            ).filter { SundayAttendancePolicy.isListed(it.absenceAnchorDate, today) }

    fun current(
        memberId: Long,
        today: LocalDate,
    ): LongAbsenceCandidateView? = candidates(today, memberId).firstOrNull()

    /** Actor subjects to their member profile, via the lookup hash; admin-only accounts have none. */
    fun resolveActors(subjects: Collection<String>): Map<String, LongAbsenceActorDto> {
        val bySubjectHash =
            subjects
                .distinct()
                .mapNotNull { subject ->
                    PiiCryptoContext.lookupHash(subject)?.let { it to subject }
                }.toMap()
        if (bySubjectHash.isEmpty()) return emptyMap()
        return memberRepository
            .findAllByKeycloakLookupHashInAndDeletedAtIsNull(bySubjectHash.keys)
            .mapNotNull { member -> bySubjectHash[member.keycloakLookupHash]?.let { it to member.toActor() } }
            .toMap()
    }

    private fun Member.toActor() = LongAbsenceActorDto(publicId = publicId, fullName = getFullName())
}
