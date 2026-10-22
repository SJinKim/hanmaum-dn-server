package com.hanmaum.dn.app.features.consents.service

import com.hanmaum.dn.app.common.api.ApiErrorCode
import com.hanmaum.dn.app.common.domainvalue.ConsentPurpose
import com.hanmaum.dn.app.features.consents.api.v1.dto.ConsentGrant
import com.hanmaum.dn.app.features.consents.api.v1.dto.ConsentPolicyResponse
import com.hanmaum.dn.app.features.consents.api.v1.dto.ConsentState
import com.hanmaum.dn.app.features.consents.api.v1.dto.MemberConsentStatus
import com.hanmaum.dn.app.features.consents.api.v1.dto.MemberConsentsResponse
import com.hanmaum.dn.app.features.consents.config.ConsentProperties
import com.hanmaum.dn.app.features.consents.domain.ConsentEndReason
import com.hanmaum.dn.app.features.consents.domain.ConsentSource
import com.hanmaum.dn.app.features.consents.domain.MemberConsent
import com.hanmaum.dn.app.features.consents.repository.MemberConsentRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.members.service.MemberPrincipal
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * The one place consent is recorded, checked and withdrawn (HDN-358).
 *
 * A feature that needs consent embeds a [ConsentGrant] in its request and calls [require]
 * inside its own transaction. If the feature's write fails, the grant rolls back with it, so
 * there is never a consent row for something that did not happen.
 */
@Service
class ConsentService(
    private val consentRepository: MemberConsentRepository,
    private val currentMemberResolver: CurrentMemberResolver,
    private val properties: ConsentProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(ConsentService::class.java)

    fun policies(): List<ConsentPolicyResponse> =
        ConsentPurpose.entries.map {
            ConsentPolicyResponse(purpose = it, version = properties.versionOf(it), requiredForAccount = it.requiredForAccount)
        }

    /**
     * Rejects a grant that cannot be recorded, before the caller does anything costly.
     *
     * Registration calls this ahead of creating the Keycloak user: a stale version must fail
     * the request, not leave an account behind whose consent was never written.
     */
    fun check(
        purpose: ConsentPurpose,
        grant: ConsentGrant?,
    ) {
        if (grant == null) {
            if (properties.isEnforced(purpose)) throw required()
            return
        }
        if (!grant.accepted) throw required()
        if (grant.version != properties.versionOf(purpose)) {
            throw ConsentException(
                HttpStatus.CONFLICT,
                ApiErrorCode.CONSENT_VERSION_OUTDATED,
                "개인정보 처리방침이 변경되었습니다. 새 내용을 확인한 뒤 다시 동의해 주세요.",
            )
        }
    }

    /**
     * Makes sure [member] has consented to [purpose] for this action, recording [grant] if given.
     *
     * With no grant in the request, a standing grant of the version in force is enough, so a
     * member who agreed once is not asked on every 양육 application. Without either, an
     * enforced purpose fails with CONSENT_REQUIRED and an unenforced one passes unrecorded.
     */
    @Transactional
    fun require(
        member: Member,
        purpose: ConsentPurpose,
        grant: ConsentGrant?,
        source: ConsentSource,
    ): MemberConsent? {
        if (grant == null) {
            val standing = member.id?.let { consentRepository.findStanding(it, purpose) }
            if (standing != null && standing.policyVersion == properties.versionOf(purpose)) return standing
        }
        check(purpose, grant)
        return grant?.let { record(member, purpose, it.version, source) }
    }

    /** GET /members/me/consents: every purpose, so the app can ask for what is missing. */
    @Transactional
    fun status(caller: MemberPrincipal): MemberConsentsResponse {
        val member = resolveMember(caller)
        val standing = consentRepository.findStanding(member.id!!).associateBy { it.purpose }
        val consents =
            ConsentPurpose.entries.map { purpose ->
                val current = properties.versionOf(purpose)
                val grant = standing[purpose]
                MemberConsentStatus(
                    purpose = purpose,
                    state =
                        when {
                            grant == null -> ConsentState.MISSING
                            grant.policyVersion == current -> ConsentState.GRANTED
                            else -> ConsentState.OUTDATED
                        },
                    requiredForAccount = purpose.requiredForAccount,
                    currentVersion = current,
                    grantedVersion = grant?.policyVersion,
                    grantedAt = grant?.grantedAt,
                )
            }
        return MemberConsentsResponse(
            actionRequired = consents.any { it.requiredForAccount && it.state != ConsentState.GRANTED },
            consents = consents,
        )
    }

    /** POST /members/me/consents: the member agrees on a consent screen outside any other flow. */
    @Transactional
    fun grant(
        caller: MemberPrincipal,
        purpose: ConsentPurpose,
        grant: ConsentGrant,
    ): MemberConsentStatus {
        val member = resolveMember(caller)
        check(purpose, grant)
        val consent = record(member, purpose, grant.version, ConsentSource.IN_APP)
        return MemberConsentStatus(
            purpose = purpose,
            state = ConsentState.GRANTED,
            requiredForAccount = purpose.requiredForAccount,
            currentVersion = consent.policyVersion,
            grantedVersion = consent.policyVersion,
            grantedAt = consent.grantedAt,
        )
    }

    /**
     * DELETE /members/me/consents/{purpose}: as easy as granting (GDPR Art. 7(3)).
     *
     * Idempotent: withdrawing what is not standing is not an error, the member's intent holds.
     * Withdrawal is not retroactive, so it ends the grant rather than deleting it.
     */
    @Transactional
    fun withdraw(
        caller: MemberPrincipal,
        purpose: ConsentPurpose,
    ) {
        val member = resolveMember(caller)
        val standing = consentRepository.findStanding(member.id!!, purpose) ?: return
        standing.end(ConsentEndReason.WITHDRAWN, Instant.now(clock))
        consentRepository.save(standing)
        log
            .atInfo()
            .addKeyValue("event.action", "consent.withdraw")
            .addKeyValue("member.id", member.id)
            .addKeyValue("consent.purpose", purpose)
            .log("Consent withdrawn")
    }

    /** Writes a grant, ending an older standing one. The same version again is a no-op. */
    private fun record(
        member: Member,
        purpose: ConsentPurpose,
        version: String,
        source: ConsentSource,
    ): MemberConsent {
        val now = Instant.now(clock)
        val standing = member.id?.let { consentRepository.findStanding(it, purpose) }
        if (standing != null) {
            if (standing.policyVersion == version) return standing
            standing.end(ConsentEndReason.SUPERSEDED, now)
            // Flushed before the insert: uq_member_consent_standing would see two standing rows.
            consentRepository.saveAndFlush(standing)
        }
        val saved =
            consentRepository.save(
                MemberConsent(member = member, purpose = purpose, policyVersion = version, source = source, grantedAt = now),
            )
        log
            .atInfo()
            .addKeyValue("event.action", "consent.grant")
            .addKeyValue("member.id", member.id)
            .addKeyValue("consent.purpose", purpose)
            .addKeyValue("consent.version", version)
            .addKeyValue("consent.source", source)
            .log("Consent granted")
        return saved
    }

    private fun required() = ConsentException(HttpStatus.BAD_REQUEST, ApiErrorCode.CONSENT_REQUIRED, "개인정보 수집·이용에 동의해야 합니다.")

    private fun resolveMember(caller: MemberPrincipal): Member =
        currentMemberResolver.resolveAndLink(caller.subject, caller.email, caller.emailVerified)
}
