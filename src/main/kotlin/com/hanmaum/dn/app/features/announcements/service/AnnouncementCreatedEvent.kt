package com.hanmaum.dn.app.features.announcements.service

import java.util.UUID

data class AnnouncementCreatedEvent(
    val announcementPublicId: UUID,
    val announcementTitle: String,
)
