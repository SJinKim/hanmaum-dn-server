package com.hanmaum.dn.app.features.training.domain

/**
 * Distinguishes repeatable takes of the same course.
 *
 * 성경개관 (Bible Overview) is taken once for the Old Testament and once for the New,
 * so one member legitimately holds two rows for that course. The workbook records this
 * as 구약 / 신약 inside the free-text 기타 column.
 */
enum class TrainingVariant {
    OLD_TESTAMENT,
    NEW_TESTAMENT,
}
