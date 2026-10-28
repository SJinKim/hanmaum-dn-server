package com.hanmaum.dn.app.features.consents.api.v1.dto

import com.hanmaum.dn.app.common.domainvalue.ConsentPurpose
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * A member's agreement to one policy text, embeddable in any request that needs consent.
 *
 * [version] is the version the app displayed, taken from GET /consents/policies. Sending it
 * back, rather than letting the server stamp its current one, is what makes the record true:
 * a client showing a stale text gets 409 CONSENT_VERSION_OUTDATED instead of a row claiming
 * agreement to a text the member never saw.
 */
data class ConsentGrant(
    // Not bean-validated: an unticked box must reach ConsentService.check() and fail with the
    // documented CONSENT_REQUIRED code instead of a code-less validation 400.
    val accepted: Boolean,
    @field:NotBlank
    @field:Size(max = 50)
    val version: String,
)

/** POST /members/me/consents */
data class GrantConsentRequest(
    @field:NotNull
    val purpose: ConsentPurpose?,
    @field:Valid
    @field:NotNull
    val consent: ConsentGrant?,
)

/** One purpose and the version a client must show and send back. */
data class ConsentPolicyResponse(
    val purpose: ConsentPurpose,
    val version: String,
    val requiredForAccount: Boolean,
)

enum class ConsentState {
    /** A standing grant of the version in force. */
    GRANTED,

    /** A standing grant of an older version; ask again. */
    OUTDATED,

    /** Never granted, or withdrawn. */
    MISSING,
}

data class MemberConsentStatus(
    val purpose: ConsentPurpose,
    val state: ConsentState,
    val requiredForAccount: Boolean,
    val currentVersion: String,
    val grantedVersion: String?,
    val grantedAt: Instant?,
)

/** GET /members/me/consents */
data class MemberConsentsResponse(
    /** True if a purpose required for the account is not GRANTED; the app should ask now. */
    val actionRequired: Boolean,
    val consents: List<MemberConsentStatus>,
)
