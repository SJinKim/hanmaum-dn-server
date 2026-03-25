package com.hanmaum.dn.app.features.announcements.domain

import com.hanmaum.dn.app.common.domainvalue.AnnouncementCategory
import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "announcements")
class Announcement(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var category: AnnouncementCategory,
    @Column(nullable = false)
    var title: String,
    @Column(columnDefinition = "TEXT", nullable = false)
    var body: String,
    @Column(name = "start_at", nullable = false)
    var startAt: LocalDateTime,
    @Column(name = "end_at")
    var endAt: LocalDateTime? = null,
    @Column(name = "is_pinned")
    var isPinned: Boolean = false,
) : BaseEntity()
