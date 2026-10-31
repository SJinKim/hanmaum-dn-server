package com.hanmaum.dn.app.features.attendance.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.common.pii.EncryptedAbsenceFollowupContactedByConverter
import com.hanmaum.dn.app.common.pii.EncryptedAbsenceFollowupMemoConverter
import com.hanmaum.dn.app.common.pii.EncryptedAbsenceFollowupReturnedByConverter
import com.hanmaum.dn.app.common.pii.PiiCryptoContext
import com.hanmaum.dn.app.features.members.domain.Member
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
import jakarta.persistence.Version
import java.time.Instant
import java.time.LocalDate

/**
 * One absence phase of a 장기결석자 and what a pastor or admin recorded about it (HDN-375, HDN-379).
 *
 * A phase counts from [absenceAnchorDate], the later of the last qualifying Sunday check-in
 * ([lastSundayDate]) and a manual return. It is stored only once something happened to it and is
 * never reopened once [closedOn] is set: a later absence is a new phase with its own
 * [phaseGeneration] and public id, even when [lastSundayDate] is the same. [closedOn] starts the
 * retention: the memo goes after 90 days, the phase after ten years (decision #380).
 */
@Entity
@Table(name = "attendance_absence_followups")
class AttendanceAbsenceFollowup(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    val member: Member,
    @Column(name = "last_sunday_date", nullable = false, updatable = false)
    val lastSundayDate: LocalDate,
    @Column(name = "absence_anchor_date", nullable = false, updatable = false)
    val absenceAnchorDate: LocalDate,
    /** The member's conflict counter when the phase was stored; 0 for phases from before HDN-379. */
    @Column(name = "phase_generation", nullable = false, updatable = false)
    val phaseGeneration: Long,
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

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 32)
    var endReason: AbsencePhaseEndReason? = null
        protected set

    @Column(name = "end_recorded_at")
    var endRecordedAt: Instant? = null
        protected set

    /** Keycloak subject of whoever marked the member as back; only for a manual return. */
    @Convert(converter = EncryptedAbsenceFollowupReturnedByConverter::class)
    @Column(name = "returned_by", columnDefinition = "TEXT")
    var returnedBy: String? = null
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

    /** Ends the phase by a manual return on [on]; the caller has checked that it is current. */
    fun closeByManualReturn(
        on: LocalDate,
        actorSubject: String,
        at: Instant,
    ) {
        check(closedOn == null) { "Phase already closed" }
        closedOn = on
        endReason = AbsencePhaseEndReason.MANUAL_RETURN
        endRecordedAt = at
        returnedBy = actorSubject
    }

    /** Ends the phase by the qualifying check-in on [on]; no actor, because nobody confirmed it. */
    fun closeByCheckIn(
        on: LocalDate,
        at: Instant,
    ) {
        check(closedOn == null) { "Phase already closed" }
        closedOn = on
        endReason = AbsencePhaseEndReason.CHECK_IN
        endRecordedAt = at
    }

    @PrePersist
    @PreUpdate
    fun refreshPiiKeyId() {
        piiKeyId = PiiCryptoContext.storageKeyId()
    }

    override fun toString(): String =
        "AttendanceAbsenceFollowup(id=$id, lastSundayDate=$lastSundayDate, absenceAnchorDate=$absenceAnchorDate, " +
            "phaseGeneration=$phaseGeneration, contacted=$contacted, closedOn=$closedOn, endReason=$endReason, version=$version)"
}
