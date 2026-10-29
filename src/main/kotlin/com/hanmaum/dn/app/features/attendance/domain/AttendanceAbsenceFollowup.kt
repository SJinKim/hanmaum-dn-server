package com.hanmaum.dn.app.features.attendance.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.common.pii.EncryptedAbsenceFollowupContactedByConverter
import com.hanmaum.dn.app.common.pii.EncryptedAbsenceFollowupMemoConverter
import com.hanmaum.dn.app.common.pii.PiiCryptoContext
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.time.LocalDate

/**
 * What a pastor or admin recorded about one absence phase of a 장기결석자 (HDN-375).
 *
 * The phase is `(member, lastSundayDate)`: the date of the last qualifying Sunday check-in.
 * A later qualifying check-in starts a new phase, so a returning member never carries an old
 * memo into the next absence. [closedOn] marks that return and starts the 90-day retention.
 */
@Entity
@Table(name = "attendance_absence_followups")
class AttendanceAbsenceFollowup(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    val member: Member,
    @Column(name = "last_sunday_date", nullable = false, updatable = false)
    val lastSundayDate: LocalDate,
) : BaseEntity() {
    @Column(name = "contacted", nullable = false)
    var contacted: Boolean = false
        protected set

    /** Keycloak subject of whoever marked the phase as contacted; never taken from a request body. */
    @Convert(converter = EncryptedAbsenceFollowupContactedByConverter::class)
    @Column(name = "contacted_by", columnDefinition = "TEXT")
    var contactedBy: String? = null
        protected set

    @Column(name = "contacted_at")
    var contactedAt: Instant? = null
        protected set

    @Convert(converter = EncryptedAbsenceFollowupMemoConverter::class)
    @Column(name = "memo", columnDefinition = "TEXT")
    var memo: String? = null

    @Column(name = "closed_on")
    var closedOn: LocalDate? = null
        protected set

    @Column(name = "pii_key_id", length = 50)
    var piiKeyId: String? = null

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0

    /**
     * Sets the contact status. Marking an already contacted phase again keeps the original actor
     * and time; taking it back clears both but leaves the memo alone.
     */
    fun markContacted(
        contacted: Boolean,
        actorSubject: String,
        at: Instant,
    ) {
        if (contacted == this.contacted) return
        this.contacted = contacted
        contactedBy = actorSubject.takeIf { contacted }
        contactedAt = at.takeIf { contacted }
    }

    /**
     * Reopens a row whose anchor became current again after a log correction. Nothing written
     * for the closed phase is carried over; the next write starts from the empty state.
     */
    fun reopen() {
        contacted = false
        contactedBy = null
        contactedAt = null
        memo = null
        closedOn = null
    }

    @PrePersist
    @PreUpdate
    fun refreshPiiKeyId() {
        piiKeyId = PiiCryptoContext.storageKeyId()
    }

    override fun toString(): String =
        "AttendanceAbsenceFollowup(id=$id, lastSundayDate=$lastSundayDate, contacted=$contacted, closedOn=$closedOn, version=$version)"
}
