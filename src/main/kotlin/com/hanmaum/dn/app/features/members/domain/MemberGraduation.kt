package com.hanmaum.dn.app.features.members.domain

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.common.pii.EncryptedGraduationNoteConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * One member's departure from the DN community.
 *
 * At most one open graduation (revertedAt IS NULL) may exist per member. That is enforced
 * by the partial unique index uq_member_graduations_open, not declared here: JPA cannot
 * express a partial index, and Hibernate's schema validation does not check unique
 * constraints, so a @Table declaration would silently drift from the database.
 */
@Entity
@Table(name = "member_graduations")
class MemberGraduation(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    var member: Member,
    @Column(name = "graduated_on", nullable = false)
    var graduatedOn: LocalDate,
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    var reason: GraduationReason,
    /** Keycloak subject of the admin who recorded this. Pseudonymous, not a name. */
    @Column(name = "graduated_by", nullable = false, length = 64)
    var graduatedBy: String,
    /**
     * The member's status immediately before graduating, restored on reinstatement.
     * Stored as a String, not the enum: it is a historical snapshot, and a value that
     * later leaves MemberStatus must still load.
     */
    @Column(name = "previous_member_status", nullable = false, length = 20)
    var previousMemberStatus: String,
    @Convert(converter = EncryptedGraduationNoteConverter::class)
    @Column(name = "note", columnDefinition = "TEXT")
    var note: String? = null,
    @Column(name = "reverted_at")
    var revertedAt: Instant? = null,
    @Column(name = "reverted_by", length = 64)
    var revertedBy: String? = null,
) : BaseEntity() {
    constructor(
        member: Member,
        graduatedOn: LocalDate,
        reason: GraduationReason,
        graduatedBy: String,
        previousMemberStatus: MemberStatus,
        note: String? = null,
    ) : this(
        member = member,
        graduatedOn = graduatedOn,
        reason = reason,
        graduatedBy = graduatedBy,
        previousMemberStatus = previousMemberStatus.name,
        note = note,
    )

    fun isOpen(): Boolean = revertedAt == null

    // Hand-written: plain JPA entity, and touching the lazy `member` association would
    // risk a LazyInitializationException. Only non-PII fields — never the note.
    override fun toString(): String = "MemberGraduation(id=$id, publicId=$publicId, reason=$reason, open=${isOpen()})"
}
