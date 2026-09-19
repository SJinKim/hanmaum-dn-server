package com.hanmaum.dn.app.features.members.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "member_claim_conflicts")
class MemberClaimConflict(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_member_id", nullable = false)
    val registrationMember: Member,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_member_id", nullable = false)
    val candidateMember: Member,
    @Column(nullable = false, length = 64)
    val reason: String,
    @Column(name = "conflict_fields", length = 500)
    val conflictFields: String? = null,
) : BaseEntity()
