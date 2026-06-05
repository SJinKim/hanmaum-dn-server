package com.hanmaum.dn.app.features.ministry.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

/**
 * A member's assignment to a [Ministry] for a date range. [startDate] is the
 * first-of-month the assignment began; [endDate] is the first-of-month it ended,
 * or null while the member is currently active in the ministry.
 * Physical table is still `ministry_registrations` (entity renamed only).
 */
@Entity
@Table(name = "ministry_registrations")
class MinistryAssignment(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ministry_id", nullable = false)
    var ministry: Ministry,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    var member: Member,
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "end_date")
    var endDate: LocalDate? = null,
    @Column(columnDefinition = "TEXT", length = 500)
    var note: String? = null,
) : BaseEntity() {
    @Column(name = "delete_entry_at")
    var deleteEntryAt: Instant? = null
}
