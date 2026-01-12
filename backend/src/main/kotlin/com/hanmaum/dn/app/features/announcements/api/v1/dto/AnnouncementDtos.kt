package com.hanmaum.dn.app.features.announcements.api.v1.dto

import java.time.LocalDateTime

// Was die App empfängt
data class AnnouncementDto(
    val id: Long,
    val title: String,
    val body: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime?,
    val isPinned: Boolean,
    val createdAt: LocalDateTime?
)

// Was der Admin (später Dashboard, jetzt via Swagger) sendet
data class CreateAnnouncementRequest(
    val title: String,
    val body: String,
    val startAt: LocalDateTime = LocalDateTime.now(),
    val endAt: LocalDateTime? = null,
    val isPinned: Boolean = false
)