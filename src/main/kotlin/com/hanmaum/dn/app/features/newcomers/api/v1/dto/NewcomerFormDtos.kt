package com.hanmaum.dn.app.features.newcomers.api.v1.dto

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.features.newcomers.domain.ChurchExperience
import dev.zacsweers.redacted.annotations.Redacted
import dev.zacsweers.redacted.annotations.Unredacted
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

data class CreateFormLinkRequest(
    val expiresAt: Instant? = null,
)

data class FormLinkResponse(
    val publicId: String,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val active: Boolean,
    val useCount: Long,
    val token: String? = null,
)

data class PublicFormMetadataResponse(
    val expiresAt: Instant,
    val consentVersion: String,
)

@Redacted
data class PublicNewcomerSubmissionRequest(
    @field:NotBlank val lastName: String,
    @field:NotBlank val firstName: String,
    @field:NotBlank val englishName: String,
    val gender: Gender,
    val birthDate: LocalDate,
    @field:Size(max = 50) val phoneNumber: String,
    val churchExperience: ChurchExperience,
    val baptism: Baptism,
    @field:NotEmpty val visitMotives: List<@NotBlank String>,
    @field:Email val email: String? = null,
    val kakaoId: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val previousChurch: String? = null,
    @field:AssertTrue val consentAccepted: Boolean,
    val honeypot: String? = null,
)

data class PublicSubmissionResponse(
    @Unredacted val newcomerPublicId: String,
    @Unredacted val submittedAt: Instant?,
)
