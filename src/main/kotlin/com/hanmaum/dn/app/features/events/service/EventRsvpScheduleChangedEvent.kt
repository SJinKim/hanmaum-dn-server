package com.hanmaum.dn.app.features.events.service

import java.time.OffsetDateTime
import java.util.UUID

data class EventRsvpScheduleChangedEvent(
    val eventRsvpId: Long,
    val eventPublicId: UUID,
    val eventTitle: String,
    val previousWindowStart: OffsetDateTime,
    val previousWindowEnd: OffsetDateTime,
    val currentWindowStart: OffsetDateTime,
    val currentWindowEnd: OffsetDateTime,
)
