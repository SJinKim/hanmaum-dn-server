package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

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
) {
    /**
     * Resolves by Keycloak subject alone. Read-only, and the right choice wherever the email
     * claims are not at hand.
     */
    fun require(keycloakSubject: String): Member =
        memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)
            ?: throw MemberProfileNotFoundException()

    /**
     * Resolves by subject and, failing that, adopts a legacy row that carries the same
     * verified email — a member who existed before Keycloak ids were stored.
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
    ): Member {
        memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)?.let { return it }

        // An unverified email is not proof of ownership, so it cannot adopt a row.
        if (!emailVerified || email.isNullOrBlank()) throw MemberProfileNotFoundException()

        val legacyMember = memberRepository.findByEmailAndDeletedAtIsNull(email) ?: throw MemberProfileNotFoundException()
        if (legacyMember.keycloakId != null && legacyMember.keycloakId != keycloakSubject) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Member profile is linked to another identity.")
        }

        legacyMember.keycloakId = keycloakSubject
        return memberRepository.save(legacyMember)
    }
}
