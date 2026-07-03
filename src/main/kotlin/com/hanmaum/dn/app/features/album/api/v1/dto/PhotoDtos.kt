package com.hanmaum.dn.app.features.album.api.v1.dto

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PhotoDto(
    val photoId: UUID,
    val thumbUrl: String?,
    val originalUrl: String,
    val takenAt: Instant?,
    val uploadedBy: String,
    val mine: Boolean,
    val saved: Boolean,
)

data class PhotoPageDto(
    val items: List<PhotoDto>,
    val page: Int,
    val totalPages: Int,
    val totalItems: Long,
)

data class CalendarDayDto(
    val date: LocalDate,
    val photoCount: Long,
    val coverThumbUrl: String?,
)
