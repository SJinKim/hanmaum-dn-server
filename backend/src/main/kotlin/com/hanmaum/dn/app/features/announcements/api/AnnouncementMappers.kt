package com.hanmaum.dn.app.features.announcements.api

import com.hanmaum.dn.app.features.announcements.api.v1.dto.AnnouncementDto
import com.hanmaum.dn.app.features.announcements.api.v1.dto.CreateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.domain.Announcement

fun Announcement.toDto() = AnnouncementDto(
    id = this.id!!,
    title = this.title,
    body = this.body,
    startAt = this.startAt,
    endAt = this.endAt,
    isPinned = this.isPinned,
    createdAt = this.createdAt // oder createdDate aus BaseEntity
)

fun CreateAnnouncementRequest.toEntity() = Announcement(
    title = this.title,
    body = this.body,
    startAt = this.startAt,
    endAt = this.endAt,
    isPinned = this.isPinned
)