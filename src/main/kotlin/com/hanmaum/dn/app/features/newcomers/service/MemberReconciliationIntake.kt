package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.domain.MemberReconciliation
import com.hanmaum.dn.app.features.newcomers.domain.ReconciliationReason
import com.hanmaum.dn.app.features.newcomers.domain.ReconciliationStatus
import com.hanmaum.dn.app.features.newcomers.repository.MemberReconciliationRepository
import org.springframework.stereotype.Component
import java.text.Normalizer

/**
 * Opens reconciliation cases for both ways a person enters: the app registration and the
 * public newcomer form (#270).
 *
 * Only a verified email together with the same name and birth date may merge on its own,
 * and that stays in [com.hanmaum.dn.app.features.members.service.CurrentMemberResolver].
 * Everything this class finds is a suspicion and ends up in front of a person.
 */
@Component
class MemberReconciliationIntake(
    private val memberRepository: MemberRepository,
    private val reconciliationRepository: MemberReconciliationRepository,
) {
    /**
     * Records one case per registration. A case that is still open or was dismissed is not
     * reopened, so a dismissed suspicion does not come back on the next request.
     */
    fun open(
        registration: Member,
        candidates: List<Member>,
        reasons: List<ReconciliationReason>,
        conflicts: Set<String> = emptySet(),
    ): Boolean {
        val registrationId = registration.id ?: return false
        if (candidates.isEmpty()) return false
        if (reconciliationRepository.findAllByRegistrationMemberIdAndDeletedAtIsNull(registrationId).any {
                it.status in setOf(ReconciliationStatus.OPEN, ReconciliationStatus.DISMISSED)
            }
        ) {
            return false
        }
        val review =
            MemberReconciliation(
                registration,
                reasons.joinToString(",") { it.name },
                conflicts.sorted().joinToString(",").ifBlank { null },
            )
        review.candidateMemberIds += candidates.mapNotNull(Member::id)
        reconciliationRepository.save(review)
        return true
    }

    /** Searches by name plus birth date or phone number and opens a case for any hit. */
    fun openForPossibleMatches(registration: Member): Boolean {
        val candidates = findPossibleMatches(registration)
        if (candidates.isEmpty()) return false
        val reasons =
            if (candidates.size > 1) {
                listOf(ReconciliationReason.POSSIBLE_NAME_BIRTH_MATCH, ReconciliationReason.MULTIPLE_CANDIDATES)
            } else {
                listOf(ReconciliationReason.POSSIBLE_NAME_BIRTH_MATCH)
            }
        return open(registration, candidates, reasons)
    }

    /**
     * Same normalized name, and either the same birth date or the same phone number. An app
     * account is never offered another app account: linking moves one Keycloak subject onto
     * the chosen member, and a member holds only one.
     */
    fun findPossibleMatches(registration: Member): List<Member> {
        val name = normalizeName(registration.lastName, registration.firstName)
        val phone = normalizePhone(registration.phoneNumber)
        val birthDate = registration.birthDate
        if (birthDate == null && phone == null) return emptyList()
        return memberRepository
            .findAllByDeletedAtIsNull()
            .filter { it.id != registration.id }
            .filter { registration.keycloakId == null || it.keycloakId == null }
            .filter { normalizeName(it.lastName, it.firstName) == name }
            .filter {
                (birthDate != null && it.birthDate == birthDate) ||
                    (phone != null && normalizePhone(it.phoneNumber) == phone)
            }
    }

    companion object {
        private val MULTIPLE_WHITESPACE = Regex("\\s+")
        private val NON_DIGIT = Regex("\\D")

        fun sameIdentity(
            registration: Member,
            candidate: Member,
        ): Boolean =
            registration.birthDate != null &&
                registration.birthDate == candidate.birthDate &&
                normalizeName(registration.lastName, registration.firstName) ==
                normalizeName(candidate.lastName, candidate.firstName)

        /**
         * Hangul names drop all whitespace, because 홍길동 is split into family and given name
         * differently from form to form. Other scripts keep word boundaries and ignore case.
         */
        fun normalizeName(
            lastName: String,
            firstName: String,
        ): String {
            val normalized = Normalizer.normalize("$lastName $firstName", Normalizer.Form.NFC)
            return if (normalized.all { it.isWhitespace() || Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HANGUL }) {
                normalized.filterNot(Char::isWhitespace)
            } else {
                normalized.trim().replace(MULTIPLE_WHITESPACE, " ").lowercase()
            }
        }

        /** Digits only, with a German country prefix folded into the national 0. */
        fun normalizePhone(phone: String?): String? {
            if (phone.isNullOrBlank()) return null
            val digits = phone.replace(NON_DIGIT, "")
            val national =
                when {
                    digits.startsWith("0049") -> "0" + digits.removePrefix("0049")
                    phone.trim().startsWith("+49") -> "0" + digits.removePrefix("49")
                    else -> digits
                }
            return national.takeIf { it.length >= 6 }
        }
    }
}
