package com.hanmaum.dn.app.features.training.api.v1.dto

/** A training catalog entry. publicId is the external identifier; internal id is never exposed. */
data class TrainingDto(
    val publicId: String,
    val name: String,
    val sortOrder: Int,
)
