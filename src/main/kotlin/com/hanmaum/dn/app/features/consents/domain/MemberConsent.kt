package com.hanmaum.dn.app.features.consents.domain

import com.hanmaum.dn.app.common.domainvalue.ConsentPurpose
import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/** Where a grant was given. Kept so the record shows which screen the member agreed on. */
enum class ConsentSource {
    REGISTRATION,
    IN_APP,
    COURSE_APPLICATION,
}

/** Why a grant stopped standing. */
enum class ConsentEndReason {
    /** The member took it back (GDPR Art. 7(3)). */
    WITHDRAWN,

    /** The member agreed to a newer version of the same purpose. */
    SUPERSEDED,
}

/**
 * One grant of consent for one purpose, as proof under GDPR Art. 7(1).
 *
 * Append-only: a grant is ended, never edited or deleted, so what the member agreed to stays
 * on record. The partial unique index uq_member_consent_standing (see the migration) allows
 * one standing grant per member and purpose; it is not declared here because JPA cannot say
 * "WHERE ended_at IS NULL".
 */
@Entity
@Table(name = "member_consents")
class MemberConsent(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    val member: Member,
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false)
    val purpose: ConsentPurpose,
    @Column(name = "policy_version", nullable = false, updatable = false)
    val policyVersion: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false)
    val source: ConsentSource,
    @Column(name = "granted_at", nullable = false, updatable = false)
    val grantedAt: Instant,
) : BaseEntity() {
    @Column(name = "ended_at")
    var endedAt: Instant? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason")
    var endReason: ConsentEndReason? = null
        protected set

    val isStanding: Boolean get() = endedAt == null

    fun end(
        reason: ConsentEndReason,
        at: Instant,
    ) {
        check(isStanding) { "Consent already ended" }
        endedAt = at
        endReason = reason
    }

    override fun toString(): String = "MemberConsent(publicId=$publicId, purpose=$purpose, policyVersion=$policyVersion)"
}
