package com.hanmaum.dn.app.features.notifications.api

import com.hanmaum.dn.app.features.notifications.api.v1.dto.NotificationResponse
import com.hanmaum.dn.app.features.notifications.domain.AppNotification

fun AppNotification.toDto(): NotificationResponse =
    NotificationResponse(
        publicId = publicId,
        // The stored strings, not the parsed enums: a value this build does not recognise
        // still reaches the client, which knows how to render an unfamiliar type.
        type = typeName,
        title = title,
        body = body,
        referenceType = referenceTypeName,
        referencePublicId = referencePublicId,
        createdAt = createdAt!!,
        seenAt = seenAt,
        readAt = readAt,
    )
