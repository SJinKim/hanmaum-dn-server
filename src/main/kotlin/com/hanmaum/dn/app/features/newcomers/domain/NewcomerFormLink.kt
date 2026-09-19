package com.hanmaum.dn.app.features.newcomers.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "newcomer_form_links")
class NewcomerFormLink(
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "created_by", nullable = false, length = 64)
    val createdBy: String,
) : BaseEntity() {
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    @Column(name = "use_count", nullable = false)
    var useCount: Long = 0

    fun isActive(now: Instant): Boolean = deletedAt == null && revokedAt == null && now.isBefore(expiresAt)
}

@Entity
@Table(name = "newcomer_form_submissions")
class NewcomerFormSubmission(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_link_id", nullable = false)
    val formLink: NewcomerFormLink,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "newcomer_profile_id", nullable = false)
    val newcomerProfile: NewcomerProfile,
    @Column(name = "idempotency_hash", nullable = false, length = 64)
    val idempotencyHash: String,
) : BaseEntity()
