package com.hanmaum.dn.app.common.domainvalue

/**
 * What a member consents to. Each purpose has its own policy text and version.
 *
 * A new place that needs consent adds a purpose here and a version under
 * `app.consents.versions`; nothing else in the consent module changes.
 */
enum class ConsentPurpose(
    /** True if a member account cannot be used without it, so the app must ask for it. */
    val requiredForAccount: Boolean,
) {
    /** 개인정보 수집·이용 for the app account, including 세례 and 소속 (GDPR Art. 9). */
    APP_PRIVACY(requiredForAccount = true),

    /** Passing the applicant's data to application.hanmaum.de for a 양육 course. */
    COURSE_APPLICATION(requiredForAccount = false),
}
