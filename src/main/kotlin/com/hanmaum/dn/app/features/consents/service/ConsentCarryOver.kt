package com.hanmaum.dn.app.features.consents.service

import com.hanmaum.dn.app.features.consents.domain.ConsentEndReason
import com.hanmaum.dn.app.features.consents.repository.MemberConsentRepository
import com.hanmaum.dn.app.features.members.domain.Member
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Keeps consent with the person when two member rows become one (HDN-358).
 *
 * Registering against an existing member stores the grant on a temporary registration row,
 * which the claim or a manual link then deletes. Without this the proof would be lost with
 * that row, and the surviving member would read as never having agreed. No new consent is
 * invented: the rows the person gave are moved as they are.
 */
@Component
class ConsentCarryOver(
    private val consentRepository: MemberConsentRepository,
) {
    private val log = LoggerFactory.getLogger(ConsentCarryOver::class.java)

    @Transactional
    fun carryOver(
        from: Member,
        to: Member,
    ) {
        val fromId = from.id ?: return
        val toId = to.id ?: return
        if (fromId == toId) return

        // uq_member_consent_standing allows one standing grant per purpose. Where both rows
        // have one, the older is superseded by the newer, as if the person had agreed again.
        val target = consentRepository.findStanding(toId).associateBy { it.purpose }
        consentRepository.findStanding(fromId).forEach { incoming ->
            val existing = target[incoming.purpose] ?: return@forEach
            val (older, newer) =
                if (existing.grantedAt <= incoming.grantedAt) existing to incoming else incoming to existing
            older.end(ConsentEndReason.SUPERSEDED, newer.grantedAt)
            consentRepository.saveAndFlush(older)
        }

        val moved = consentRepository.moveAll(fromId, toId)
        if (moved > 0) log.info("Consent carried over fromMemberId={} toMemberId={} rows={}", fromId, toId, moved)
    }
}
