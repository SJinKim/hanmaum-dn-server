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

    /**
     * An attendance definition's check-in window overlaps another active one on the same day.
     *
     * Check-in picks a single window, so two windows that share a minute would leave the
     * later one unreachable for that stretch. fieldErrors names windowStart and windowEnd,
     * and conflictingDefinition names the window it collided with, so the admin can move
     * one of the two. Windows that merely touch (one ends when the next starts) are fine.
     */
    ATTENDANCE_WINDOW_OVERLAP,

    /**
     * A 주보 is missing a field it needs before it can be published.
     *
     * fieldErrors names each one (sermonTitle, sermonPreacher, songs), so the editor can mark
     * them. The draft itself is unchanged and can still be saved as it is.
     */
    BULLETIN_INCOMPLETE,

    /**
     * An ACTIVE member would be left without a 순: the 순 was cleared, or the member was
     * approved without one.
     *
     * fieldErrors names groupPublicId, so the dashboard can mark the 순 field. PENDING and
     * INACTIVE members may still be without a 순.
     */
    MEMBER_GROUP_REQUIRED,

    /**
     * The request needs the member's consent to a data processing purpose and carried none,
     * or carried one not accepted. The app shows the consent text and asks again.
     *
     * Also 403 on any endpoint when the member lacks a standing grant required for an account
     * (missing, withdrawn or outdated). The app reads GET /members/me/consents and asks.
     */
    CONSENT_REQUIRED,

    /**
     * The consent names a policy version that is no longer in force. The app refetches
     * GET /consents/policies, shows the new text and asks again.
     */
    CONSENT_VERSION_OUTDATED,
}
