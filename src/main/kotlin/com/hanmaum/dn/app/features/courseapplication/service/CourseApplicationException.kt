package com.hanmaum.dn.app.features.courseapplication.service

import com.hanmaum.dn.app.common.api.ApiErrorCode
import org.springframework.http.HttpStatus

/**
 * A 양육 application that cannot go ahead, with the status and code the app receives.
 *
 * [message] is Korean and written for the member; the app may show it as it is.
 */
class CourseApplicationException(
    val status: HttpStatus,
    val code: ApiErrorCode,
    message: String,
    val fieldErrors: Map<String, String>? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
