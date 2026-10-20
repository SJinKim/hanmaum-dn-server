package com.hanmaum.dn.app.features.members.domain

/**
 * 신분 — what the member does for a living or study. Chosen at self-registration and
 * edited on the 새가족 page, so it lives on the member rather than the newcomer profile
 * (HDN-332). UNIVERSITY_STUDENT covers graduate students too (대학생/대학원생).
 */
enum class IdentityStatus {
    EMPLOYEE,
    UNIVERSITY_STUDENT,
    EXAM_PREPARATION,
    WORKING_HOLIDAY,
    EXCHANGE_STUDENT,
    SELF_EMPLOYED,
    EXPATRIATE,
    JOB_SEEKING,
}
