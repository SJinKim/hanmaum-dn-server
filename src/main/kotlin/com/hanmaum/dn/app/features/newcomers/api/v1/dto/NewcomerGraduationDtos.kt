package com.hanmaum.dn.app.features.newcomers.api.v1.dto

import dev.zacsweers.redacted.annotations.Redacted
import dev.zacsweers.redacted.annotations.Unredacted
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

@Redacted
data class GraduateNewcomerRequest(
    @field:NotBlank @Unredacted val groupPublicId: String,
    @field:Min(1) @field:Max(10) @Unredacted val cohortNumber: Int,
    @Unredacted val graduatedAt: LocalDate? = null,
    val assignmentReason: String? = null,
)

@Redacted
data class NewcomerGraduationResponse(
    @Unredacted val publicId: String,
    @Unredacted val newcomerPublicId: String,
    @Unredacted val memberPublicId: String,
    @Unredacted val groupPublicId: String,
    @Unredacted val cohortNumber: Int,
    @Unredacted val cohortLabel: String,
    @Unredacted val graduatedOn: LocalDate,
    val assignmentReason: String?,
)
