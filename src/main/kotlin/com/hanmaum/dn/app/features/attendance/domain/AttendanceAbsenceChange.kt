package com.hanmaum.dn.app.features.attendance.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.common.pii.EncryptedAbsenceChangeActorConverter
import com.hanmaum.dn.app.common.pii.EncryptedAbsenceChangeMemoConverter
import com.hanmaum.dn.app.common.pii.PiiCryptoContext
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

/**
 * One contact or memo change of a long-absence phase (HDN-379). Holds the new value; successive
 * MEMO_CHANGED entries are the memo versions. Written once, never updated; clearing the memo
 * hard-deletes every MEMO_CHANGED entry of the phase.
 */
@Entity
@Table(name = "attendance_absence_changes")
class AttendanceAbsenceChange private constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "followup_id", nullable = false, updatable = false)
    val followup: AttendanceAbsenceFollowup,
    /** The member's conflict counter of the write; a contact and a memo entry may share it. */
    @Column(name = "revision", nullable = false, updatable = false)
    val revision: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 32)
    val kind: AbsenceChangeKind,
    @Column(name = "occurred_at", updatable = false)
    val occurredAt: Instant,
    @Convert(converter = EncryptedAbsenceChangeActorConverter::class)
    @Column(name = "actor_subject", columnDefinition = "TEXT", updatable = false)
    val actorSubject: String,
    @Column(name = "contacted", updatable = false)
    val contacted: Boolean?,
    @Convert(converter = EncryptedAbsenceChangeMemoConverter::class)
    @Column(name = "memo", columnDefinition = "TEXT", updatable = false)
    val memo: String?,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, updatable = false, length = 32)
    val origin: AbsenceChangeOrigin = AbsenceChangeOrigin.ACTION

    @Column(name = "pii_key_id", length = 50)
    var piiKeyId: String? = null

    @PrePersist
    @PreUpdate
    fun refreshPiiKeyId() {
        piiKeyId = PiiCryptoContext.storageKeyId()
    }

    override fun toString(): String = "AttendanceAbsenceChange(id=$id, revision=$revision, kind=$kind, origin=$origin)"

    companion object {
        fun contact(
            followup: AttendanceAbsenceFollowup,
            revision: Long,
            contacted: Boolean,
            actorSubject: String,
            at: Instant,
        ) = AttendanceAbsenceChange(followup, revision, AbsenceChangeKind.CONTACT_CHANGED, at, actorSubject, contacted, memo = null)

        fun memo(
            followup: AttendanceAbsenceFollowup,
            revision: Long,
            memo: String,
            actorSubject: String,
            at: Instant,
        ) = AttendanceAbsenceChange(followup, revision, AbsenceChangeKind.MEMO_CHANGED, at, actorSubject, contacted = null, memo = memo)
    }
}
