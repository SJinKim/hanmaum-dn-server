package com.hanmaum.dn.app.features.announcements.api.v1.dto

import java.time.LocalDateTime

// Was die App empfängt
data class AnnouncementDto(
    val id: String,
    val title: String,
    val body: String,
    val category: String,
    val startAt: String,
    val endAt: String?,
    val isPinned: Boolean,
)

// Was der Admin (später Dashboard, jetzt via Swagger) sendet
data class CreateAnnouncementRequest(
    val title: String,
    val body: String,
    val startAt: LocalDateTime = LocalDateTime.now(),
    val endAt: LocalDateTime? = null,
    val isPinned: Boolean = false,
    val category: String,
)
