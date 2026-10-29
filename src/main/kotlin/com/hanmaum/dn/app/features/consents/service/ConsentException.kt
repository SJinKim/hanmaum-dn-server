package com.hanmaum.dn.app.features.consents.service

import com.hanmaum.dn.app.common.api.ApiErrorCode
import org.springframework.http.HttpStatus

/**
 * Consent that is missing or was given to a text no longer in force.
 *
 * [message] is Korean and written for the member; the app may show it as it is.
 */
class ConsentException(
    val status: HttpStatus,
    val code: ApiErrorCode,
    message: String,
) : RuntimeException(message)
