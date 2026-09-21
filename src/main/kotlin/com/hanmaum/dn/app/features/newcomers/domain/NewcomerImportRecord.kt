package com.hanmaum.dn.app.features.newcomers.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "newcomer_import_records")
class NewcomerImportRecord(
    @Column(name = "source_fingerprint", nullable = false, length = 64, updatable = false)
    val sourceFingerprint: String,
    @Column(name = "row_number", nullable = false, updatable = false)
    val rowNumber: Int,
    @Column(name = "payload_fingerprint", nullable = false, length = 64, updatable = false)
    val payloadFingerprint: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    val member: Member,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "newcomer_profile_id", nullable = false, updatable = false)
    val newcomerProfile: NewcomerProfile,
) : BaseEntity()
