package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.domain.MemberClaimConflict
import com.hanmaum.dn.app.features.members.repository.MemberClaimConflictRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.domain.ReconciliationReason
import com.hanmaum.dn.app.features.newcomers.repository.MemberReconciliationRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import com.hanmaum.dn.app.features.newcomers.service.MemberReconciliationIntake
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

/**
 * The caller is authenticated but owns no member row.
 *
 * Separate from a plain 404 because the two mean different things to a client: a missing
 * route is a bug to report, a missing profile is a person who is not a member of this
 * congregation yet and belongs somewhere other than the member area. Both were the same
 * bare 404 before, which cost an afternoon of looking for an endpoint that was deployed all
 * along.
 */
class MemberProfileNotFoundException(
    message: String = "Member profile not found.",
) : RuntimeException(message)

/**
 * The one place that turns a JWT into a member.
 *
 * There used to be two: [MemberService] healed a legacy account by linking it on the way
 * past, while every other reader did a bare lookup and threw. An account that worked on
 * /members/me failed on /verses/records, and each new member-bound endpoint inherited
 * whichever version its author happened to copy.
 */
@Component
class CurrentMemberResolver(
    private val memberRepository: MemberRepository,
    private val conflictRepository: MemberClaimConflictRepository,
    private val reconciliationRepository: MemberReconciliationRepository? = null,
    private val newcomerProfileRepository: NewcomerProfileRepository? = null,
) {
    private val log = LoggerFactory.getLogger(CurrentMemberResolver::class.java)
    private val intake = reconciliationRepository?.let { MemberReconciliationIntake(memberRepository, it) }

    /**
     * Resolves by Keycloak subject alone. Read-only, and the right choice wherever the email
     * claims are not at hand.
     */
    fun require(keycloakSubject: String): Member =
        memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)
            ?: throw MemberProfileNotFoundException()

    /**
     * Resolves a registration by subject. A staged registration only claims a pre-existing
     * member after Keycloak has verified its email and the stored name and birth date match.
     *
     * This writes on a read path, which is not something to spread. It is kept because the
     * alternative is worse today: a legacy account would otherwise reach the member area
     * through /members/me and then fail on every sibling endpoint, depending on which one it
     * touched first. The durable fix is to link once at registration or first login and let
     * every reader stay read-only; until then this is the one function allowed to do it.
     */
    @Transactional
    fun resolveAndLink(
        keycloakSubject: String,
        email: String?,
        emailVerified: Boolean,
        firstName: String? = null,
        lastName: String? = null,
        birthDate: LocalDate? = null,
    ): Member {
        val registration =
            memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)
                ?: throw MemberProfileNotFoundException()
        if (!emailVerified || email.isNullOrBlank() || registration.email != null) return registration
        val candidate = memberRepository.findByEmailAndDeletedAtIsNullForUpdate(email) ?: return registration
        if (candidate.id == registration.id ||
            candidate.keycloakId != null ||
            !MemberReconciliationIntake.sameIdentity(registration, candidate)
        ) {
            recordConflict(registration, candidate, "IDENTITY_MISMATCH")
            intake?.open(registration, listOf(candidate), listOf(ReconciliationReason.EMAIL_MATCH_IDENTITY_MISMATCH))
            log.info("Member claim skipped registrationId={} candidateId={} reason={}", registration.id, candidate.id, "identity_mismatch")
            return registration
        }

        val conflicts = conflictingFields(registration, candidate)
        if (conflicts.isNotEmpty()) {
            recordConflict(registration, candidate, "PROFILE_VALUE_CONFLICT", conflicts)
            intake?.open(registration, listOf(candidate), listOf(ReconciliationReason.PROFILE_VALUE_CONFLICT), conflicts)
            return registration
        }

        // Remove the staged row before assigning the subject so the partial lookup-hash
        // indexes remain valid throughout the transaction.
        registration.keycloakId = null
        registration.memberStatus = MemberStatus.DELETED
        registration.deletedAt = Instant.now()
        registration.deleteEntryAt = registration.deletedAt
        memberRepository.saveAndFlush(registration)

        candidate.keycloakId = keycloakSubject
        copyRegisteredValues(registration, candidate)
        moveNewcomerProfile(registration, candidate)
        return memberRepository.save(candidate)
    }

    private fun copyRegisteredValues(
        registration: Member,
        candidate: Member,
    ) {
        candidate.gender = candidate.gender ?: registration.gender
        candidate.phoneNumber = candidate.phoneNumber ?: registration.phoneNumber
        candidate.street = candidate.street ?: registration.street
        candidate.houseNumber = candidate.houseNumber ?: registration.houseNumber
        candidate.zipCode = candidate.zipCode ?: registration.zipCode
        candidate.city = candidate.city ?: registration.city
        candidate.baptism = candidate.baptism ?: registration.baptism
    }

    private fun moveNewcomerProfile(
        registration: Member,
        candidate: Member,
    ) {
        val profiles = newcomerProfileRepository ?: return
        val source = registration.id?.let(profiles::findByMemberIdAndDeletedAtIsNull) ?: return
        if (candidate.id?.let(profiles::findByMemberIdAndDeletedAtIsNull) == null) {
            source.member = candidate
            profiles.save(source)
        }
    }

    private fun conflictingFields(
        registration: Member,
        candidate: Member,
    ): Set<String> =
        buildSet {
            conflict("gender", registration.gender, candidate.gender)
            conflict("phoneNumber", registration.phoneNumber, candidate.phoneNumber)
            conflict("street", registration.street, candidate.street)
            conflict("houseNumber", registration.houseNumber, candidate.houseNumber)
            conflict("zipCode", registration.zipCode, candidate.zipCode)
            conflict("city", registration.city, candidate.city)
            conflict("baptism", registration.baptism, candidate.baptism)
        }

    private fun MutableSet<String>.conflict(
        field: String,
        submitted: Any?,
        canonical: Any?,
    ) {
        if (submitted != null && canonical != null && submitted != canonical) add(field)
    }

    private fun recordConflict(
        registration: Member,
        candidate: Member,
        reason: String,
        fields: Set<String> = emptySet(),
    ) {
        val registrationId = registration.id ?: return
        if (!conflictRepository.existsByRegistrationMemberIdAndDeletedAtIsNull(registrationId)) {
            conflictRepository.save(
                MemberClaimConflict(registration, candidate, reason, fields.sorted().joinToString(",").ifBlank { null }),
            )
        }
    }
}
