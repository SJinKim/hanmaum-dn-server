package com.hanmaum.dn.app.features.notifications.api.v1.dto

import com.hanmaum.dn.app.features.notifications.domain.DevicePlatform
import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val publicId: UUID,
    val type: String,
    val title: String,
    val body: String,
    val referenceType: String?,
    val referencePublicId: UUID?,
    val createdAt: Instant,
    val seenAt: Instant?,
    val readAt: Instant?,
)

data class NotificationPageResponse(
    val items: List<NotificationResponse>,
    val page: Int,
    val hasNext: Boolean,
)

data class UnseenCountResponse(
    val count: Long,
)

data class RegisterDeviceTokenRequest(
    val token: String,
    val platform: DevicePlatform,
)

data class NotificationSettingsDto(
    val pushEnabled: Boolean,
)
