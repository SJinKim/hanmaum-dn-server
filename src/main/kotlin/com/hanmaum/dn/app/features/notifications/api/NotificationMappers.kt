package com.hanmaum.dn.app.features.notifications.api

import com.hanmaum.dn.app.features.notifications.api.v1.dto.NotificationResponse
import com.hanmaum.dn.app.features.notifications.domain.AppNotification

fun AppNotification.toDto(): NotificationResponse =
    NotificationResponse(
        publicId = publicId,
        type = type.name,
        title = title,
        body = body,
        referenceType = referenceType?.name,
        referencePublicId = referencePublicId,
        createdAt = createdAt!!,
        seenAt = seenAt,
        readAt = readAt,
    )
