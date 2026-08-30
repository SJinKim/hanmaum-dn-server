package com.hanmaum.dn.app.features.announcements.domain

import com.hanmaum.dn.app.common.domainvalue.AnnouncementCategory
import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.time.OffsetDateTime

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
    var startAt: OffsetDateTime,
    @Column(name = "end_at")
    var endAt: OffsetDateTime? = null,
    @Column(name = "image_url", length = 2048)
    var imageUrl: String? = null,
    @Column(length = 255)
    var location: String? = null,
    @Column(name = "is_pinned")
    var isPinned: Boolean = false,
    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,
) : BaseEntity() {
    @Column(name = "delete_entry_at")
    var deleteEntryAt: Instant? = null
}
