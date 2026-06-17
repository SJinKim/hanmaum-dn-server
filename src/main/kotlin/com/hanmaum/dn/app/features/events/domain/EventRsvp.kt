package com.hanmaum.dn.app.features.events.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.announcements.domain.Announcement
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "event_rsvps")
class EventRsvp(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_id")
    val announcement: Announcement? = null,
    @Column(name = "title", nullable = false, length = 100)
    var title: String,
    @Column(name = "window_start", nullable = false)
    var windowStart: OffsetDateTime,
    @Column(name = "window_end", nullable = false)
    var windowEnd: OffsetDateTime,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : BaseEntity()
