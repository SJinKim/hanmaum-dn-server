package com.hanmaum.dn.app.common.security

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

internal fun securityProblemDetail(status: HttpStatus): ProblemDetail =
    ProblemDetail
        .forStatusAndDetail(
            status,
            when (status) {
                HttpStatus.UNAUTHORIZED -> "Authentication is required."
                HttpStatus.FORBIDDEN -> "Access denied."
                else -> status.reasonPhrase
            },
        ).apply {
            title = status.reasonPhrase
        }
