package com.hanmaum.dn.app.features.members.api.v1.dto

import com.hanmaum.dn.app.features.members.domain.GraduationReason
import dev.zacsweers.redacted.annotations.Redacted
import dev.zacsweers.redacted.annotations.Unredacted
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

/**
 * One graduation event. [note] is free text an admin wrote and may name a person, so the
 * DTO is redacted — it must never reach a log through toString().
 */
@Redacted
data class GraduationDto(
    @Unredacted val publicId: String,
    @Unredacted val graduatedOn: LocalDate,
    @Unredacted val reason: GraduationReason,
    val note: String? = null,
    @Unredacted val revertedAt: Instant? = null,
    /** True while this graduation still stands. */
    @Unredacted val open: Boolean,
)

/** A member's graduation state: the open event if any, plus the full history. */
data class GraduationStateDto(
    val graduated: Boolean,
    val current: GraduationDto? = null,
    val history: List<GraduationDto> = emptyList(),
)

@Redacted
data class CreateGraduationRequest(
    @field:NotNull(message = "졸업일은 필수입니다.")
    @Unredacted val graduatedOn: LocalDate? = null,
    @field:NotNull(message = "졸업 사유는 필수입니다.")
    @Unredacted val reason: GraduationReason? = null,
    @field:Size(max = 500)
    val note: String? = null,
)
