package com.hanmaum.dn.app.common.api

import java.time.OffsetDateTime
import java.time.ZoneId

data class ErrorResponse(
    val timestamp: OffsetDateTime = OffsetDateTime.now(ZoneId.of("Europe/Berlin")),
    val status: Int,
    val message: String,
    /** HTTP reason phrase, e.g. "Not Found". Human-facing and free to be reworded. */
    val error: String,
    /**
     * Stable machine-readable reason, where one exists. [error] and [message] are prose and
     * may change; this is what a client is allowed to branch on.
     */
    val code: ApiErrorCode? = null,
    /**
     * Per-field messages keyed by request property name, where the error concerns specific
     * fields — e.g. {"email": "올바른 이메일 주소여야 합니다."}. Null otherwise.
     */
    val fieldErrors: Map<String, String>? = null,
)
