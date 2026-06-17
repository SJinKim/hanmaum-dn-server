package com.hanmaum.dn.app.features.events.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "event_rsvp_logs",
    uniqueConstraints = [UniqueConstraint(name = "uq_event_rsvp_log", columnNames = ["event_rsvp_id", "member_id"])],
)
class EventRsvpLog(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_rsvp_id", nullable = false)
    val eventRsvp: EventRsvp,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id_at_rsvp")
    val groupAtRsvp: ChurchGroup? = null,
    @Column(name = "checked_in_at", nullable = false)
    val checkedInAt: Instant,
) : BaseEntity()
