package com.hanmaum.dn.app.features.training.domain

/**
 * Where a member stands in a course.
 *
 * [UNKNOWN] exists for archival records that carry no dates at all — the 역대제자반
 * cohort matrix and the 카이로스 FT sheet list participants without any timeline.
 */
enum class TrainingStatus {
    APPLIED,
    ENROLLED,
    IN_PROGRESS,
    COMPLETED,
    DROPPED,
    UNKNOWN,
}
