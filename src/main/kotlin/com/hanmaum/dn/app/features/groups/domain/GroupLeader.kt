package com.hanmaum.dn.app.features.groups.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * One member's tenure as leader of one [ChurchGroup]. A row is a *tenure*, not a person:
 * [startDate] is the day the member took over, [endDate] the day they handed over, or null
 * while they are the current leader.
 *
 * A group has at most one row with `endDate IS NULL` — enforced by the partial unique index
 * `uq_group_leaders_active_per_group`, not just by service code. Closed rows are retained so
 * past leadership stays queryable.
 *
 * Deliberately modelled as its own table rather than columns on `members`: only ~1 member per
 * group is a leader, so flags on `members` would be overwhelmingly null and could not carry
 * tenure history at all.
 */
@Entity
@Table(name = "group_leaders")
class GroupLeader(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    var group: ChurchGroup,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    var member: Member,
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,
    @Column(name = "end_date")
    var endDate: LocalDate? = null,
) : BaseEntity() {
    fun isActive(): Boolean = endDate == null && isNotDeleted()

    // Hand-written like Member.toString(): touching the lazy `group` / `member` associations
    // would risk a LazyInitializationException outside a Hibernate session, and `member`
    // carries PII that must never reach a log line (rule 10).
    override fun toString(): String = "GroupLeader(id=$id, publicId=$publicId, startDate=$startDate, endDate=$endDate)"
}
