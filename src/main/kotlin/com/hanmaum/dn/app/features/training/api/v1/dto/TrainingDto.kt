package com.hanmaum.dn.app.features.training.api.v1.dto

import java.time.LocalDate

/** A training catalog entry. publicId is the external identifier; internal id is never exposed. */
data class TrainingDto(
    val publicId: String,
    val name: String,
    val sortOrder: Int,
)

/** A catalog entry, including the fields the admin grid needs for its columns. */
data class TrainingCatalogDto(
    val publicId: String,
    val code: String,
    val name: String,
    val nameKo: String?,
    val category: String?,
    val sortOrder: Int,
    val hasCohorts: Boolean,
    val isActive: Boolean,
    val prerequisiteCode: String?,
)

/** One intake of a course that runs in cohorts, e.g. 청년 파워제자반 3기. */
data class TrainingCohortDto(
    val publicId: String,
    val series: String,
    val ordinal: Int,
    val label: String?,
    val cohortYear: Int?,
    val term: String?,
    val startedOn: LocalDate?,
    val endedOn: LocalDate?,
)
