package com.hanmaum.dn.app.common.api

import java.time.OffsetDateTime
import java.time.ZoneId

data class ErrorResponse(
    val timestamp: OffsetDateTime = OffsetDateTime.now(ZoneId.of("Europe/Berlin")),
    val status: Int,
    val message: String,
    val error: String,
)
