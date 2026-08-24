package com.hanmaum.dn.app.features.training.domain

/**
 * Which numbering a cohort belongs to.
 *
 * The 제자반 course was renumbered when it became 청년 파워제자반, so two series both
 * start at 1기. Without this discriminator the ordinal alone would not be unique.
 */
enum class CohortSeries {
    /** Pre-파워 제자반 intakes. */
    LEGACY,

    /** 파워 / 청년파워제자반 intakes. */
    POWER,
}
