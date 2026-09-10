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
}
