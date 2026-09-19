package com.hanmaum.dn.app.features.newcomers.api.v1.dto

import com.hanmaum.dn.app.features.newcomers.domain.ReconciliationStatus
import dev.zacsweers.redacted.annotations.Redacted
import dev.zacsweers.redacted.annotations.Unredacted
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.time.LocalDate

@Redacted
data class ReconciliationMemberResponse(
    @Unredacted val publicId: String,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val birthDate: LocalDate?,
    val phoneNumber: String?,
    @Unredacted val linked: Boolean,
)

@Redacted
data class ReconciliationResponse(
    @Unredacted val publicId: String,
    @Unredacted val status: ReconciliationStatus,
    @Unredacted val reasons: List<String>,
    @Unredacted val conflictFields: List<String>,
    val registrationMember: ReconciliationMemberResponse,
    val candidates: List<ReconciliationMemberResponse>,
    @Unredacted val selectedMemberPublicId: String?,
    @Unredacted val version: Long,
    @Unredacted val createdAt: Instant?,
    @Unredacted val resolvedAt: Instant?,
)

data class ResolveReconciliationRequest(
    @field:NotBlank val memberPublicId: String,
    val version: Long,
)

data class DismissReconciliationRequest(
    val version: Long,
)
