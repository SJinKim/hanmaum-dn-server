package com.hanmaum.dn.app.features.training.domain

/**
 * Stable, language-independent identifier of a catalog entry.
 *
 * Kept as a Kotlin enum so the importer and the API can refer to courses without
 * depending on display names or database ids. The values must stay in sync with the
 * `code` column seeded in V20260810120010__seed_training_catalog.sql.
 *
 * [KAIROS] and [KAIROS_FT] are discontinued and exist for archived records only; they
 * are seeded with `is_active = false`.
 */
enum class TrainingCode {
    BAPTISM_MEMBERSHIP,
    QT_BASIC_SEMINAR,
    QT_ADVANCED_SEMINAR,
    ONE_ON_ONE,
    YOUTH_POWER_DISCIPLESHIP,
    ONE_ON_ONE_SCHOOL,
    MINISTRY_CLASS,
    PROSPECTIVE_LEADER,
    BIBLE_PANORAMA,
    BIBLE_OVERVIEW,
    KAIROS,
    KAIROS_FT,
}
