package com.hanmaum.dn.app.features.groups.api.v1.dto

/**
 * Lightweight church-group entry for selection lists (e.g. the member edit form's
 * "Church Group" dropdown). publicId (UUID string) is the external identifier;
 * the internal Long id is never exposed.
 */
data class ChurchGroupSummaryDto(
    val publicId: String,
    /** Optional grouping/category (구역/부서), null when ungrouped. */
    val division: String?,
    val name: String,
)
