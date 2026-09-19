package com.hanmaum.dn.app.features.newcomers.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

enum class ReconciliationStatus { OPEN, LINKED, DISMISSED }

enum class ReconciliationReason {
    EMAIL_MATCH_IDENTITY_MISMATCH,
    POSSIBLE_NAME_BIRTH_MATCH,
    MULTIPLE_CANDIDATES,
    PROFILE_VALUE_CONFLICT,
}

@Entity
@Table(name = "member_reconciliations")
class MemberReconciliation(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_member_id", nullable = false)
    val registrationMember: Member,
    @Column(nullable = false, length = 500)
    var reasons: String,
    @Column(name = "conflict_fields", length = 500)
    var conflictFields: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReconciliationStatus = ReconciliationStatus.OPEN,
) : BaseEntity() {
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_reconciliation_candidates", joinColumns = [JoinColumn(name = "reconciliation_id")])
    @Column(name = "member_id", nullable = false)
    val candidateMemberIds: MutableSet<Long> = linkedSetOf()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_member_id")
    var selectedMember: Member? = null

    @Column(name = "resolved_by", length = 64)
    var resolvedBy: String? = null

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null

    @Version
    @Column(nullable = false)
    var version: Long = 0

    override fun toString(): String = "MemberReconciliation(id=$id, publicId=$publicId, status=$status)"
}
