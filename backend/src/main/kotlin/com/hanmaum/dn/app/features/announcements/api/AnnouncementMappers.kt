package com.hanmaum.dn.app.features.announcements.api

import com.hanmaum.dn.app.common.domainvalue.AnnouncementCategory
import com.hanmaum.dn.app.features.announcements.api.v1.dto.AnnouncementDto
import com.hanmaum.dn.app.features.announcements.api.v1.dto.CreateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.domain.Announcement

fun Announcement.toDto() = AnnouncementDto(
    id = this.publicId.toString(),
    title = this.title,
    body = this.body,
    startAt = this.startAt,
    endAt = this.endAt,
    isPinned = this.isPinned,
    category = this.category.name
)

fun CreateAnnouncementRequest.toEntity() = Announcement(
    title = this.title,
    body = this.body,
    startAt = this.startAt,
    endAt = this.endAt,
    isPinned = this.isPinned,
    category = AnnouncementCategory.valueOf(this.category)
)