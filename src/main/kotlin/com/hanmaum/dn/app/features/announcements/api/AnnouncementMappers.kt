package com.hanmaum.dn.app.features.announcements.api

import com.hanmaum.dn.app.common.domainvalue.AnnouncementCategory
import com.hanmaum.dn.app.features.announcements.api.v1.dto.AnnouncementDto
import com.hanmaum.dn.app.features.announcements.api.v1.dto.CreateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.api.v1.dto.UpdateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.domain.Announcement

fun Announcement.toDto() =
    AnnouncementDto(
        id = this.publicId.toString(),
        title = this.title,
        body = this.body,
        startAt = this.startAt.toString(),
        endAt = this.endAt?.toString(),
        imageUrl = this.imageUrl,
        location = this.location,
        viewCount = this.viewCount,
        isPinned = this.isPinned,
        category = this.category.name,
    )

fun CreateAnnouncementRequest.toEntity() =
    Announcement(
        title = this.title,
        body = this.body,
        startAt = this.startAt,
        endAt = this.endAt,
        imageUrl = this.imageUrl,
        location = this.location,
        isPinned = this.isPinned,
        category = AnnouncementCategory.valueOf(this.category),
    )

fun Announcement.applyUpdate(req: UpdateAnnouncementRequest) {
    this.title = req.title
    this.body = req.body
    this.startAt = req.startAt
    this.endAt = req.endAt
    this.imageUrl = req.imageUrl
    this.location = req.location
    this.isPinned = req.isPinned
    this.category = AnnouncementCategory.valueOf(req.category)
}
