package com.hanmaum.dn.app.common.api

/**
 * Stable machine-readable reasons, carried in [ErrorResponse.code].
 *
 * A status alone cannot say enough: a 404 for "this route does not exist" and a 404 for
 * "you have no member profile" are the same three digits and the same generic sentence, and
 * a client has to do genuinely different things about them. These names are part of the
 * contract — rename one and a client stops recognising the case.
 */
enum class ApiErrorCode {
    /**
     * The caller authenticated, but no member row belongs to them.
     *
     * Not a server fault and not a missing endpoint: the account simply is not a member of
     * this congregation yet. A client should route the person out of the member area rather
     * than showing an error and offering a retry that cannot succeed.
     */
    MEMBER_PROFILE_NOT_FOUND,

    /**
     * application.hanmaum.de could not be asked: unreachable, unconfigured, or answering in
     * a way this server cannot use. Nothing the applicant did; the app shows 준비중입니다.
     */
    COURSE_APPLICATION_UNAVAILABLE,

    /** The course is not taking applications right now. */
    COURSE_APPLICATION_CLOSED,

    /** The course has no seats left. */
    COURSE_APPLICATION_FULL,

    /** The caller already applied to, or is taking, this training. */
    COURSE_APPLICATION_ALREADY_APPLIED,

    /** The caller may not apply to this course, e.g. a 여자반 for a member who is not a woman. */
    COURSE_APPLICATION_NOT_ELIGIBLE,

    /** The application data is incomplete or was rejected; details are in fieldErrors where known. */
    COURSE_APPLICATION_INVALID,

    /** The external course does not exist or does not belong to this training. */
    COURSE_APPLICATION_COURSE_NOT_FOUND,

    /** The caller has no application to this training that could be cancelled. */
    COURSE_APPLICATION_NOT_FOUND,

    /** The application belongs to a training the caller has already completed. */
    COURSE_APPLICATION_NOT_CANCELLABLE,
}
