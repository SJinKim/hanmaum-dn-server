package com.hanmaum.dn.app.features.courseapplication.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

// Wire shapes of application.hanmaum.de (API.md in HanmaumChurch/application). Every type
// ignores unknown fields: the API adds fields in backwards-compatible releases, and a new
// field must never turn the 양육 tab into a 503.

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExternalEnvelope<T>(
    val data: T? = null,
    val meta: ExternalMeta? = null,
    val error: ExternalError? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExternalMeta(
    val idempotentReplay: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExternalError(
    val code: String = "",
    val message: String = "",
    val details: Map<String, Any?> = emptyMap(),
)

/**
 * A course as the external API lists it.
 *
 * The registration timestamps stay raw strings. The live deployment still sends
 * `2022-01-23 23:59:59` and `0000-00-00 00:00:00`; the documented contract sends ISO-8601
 * with an offset and null. [com.hanmaum.dn.app.features.courseapplication.service.RegistrationWindow]
 * reads both, so neither side has to deploy first.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ExternalCourse(
    val id: Int,
    val name: String,
    val dateText: String? = null,
    val description: String? = null,
    val secondaryText: String? = null,
    /** 0 in the legacy data means "no limit"; the documented contract uses null for that. */
    val capacity: Int? = null,
    val registrationStartsAt: String? = null,
    val registrationEndsAt: String? = null,
    val requiredOptionalFields: List<String> = emptyList(),
    val targetGroups: List<Int> = emptyList(),
    /** Null on deployments older than the dynamic-form contract. */
    val applicationFields: List<ExternalApplicationField>? = null,
    /** Null on older deployments, which accept every course through the API. */
    val apiApplicationSupported: Boolean? = null,
    val unsupportedFields: List<String> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExternalApplicationField(
    val name: String,
    val type: String? = null,
    val required: Boolean = false,
    val label: String? = null,
    val supported: Boolean = true,
    val options: List<ExternalFieldOption> = emptyList(),
    /** Required on top of [required] when every named field holds one of its values, e.g. aGroup ∈ {4, 5}. */
    val requiredWhen: Map<String, List<String>> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExternalFieldOption(
    val value: String,
    val label: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExternalApplication(
    val id: Long,
    val courseId: Int,
    /** `active` or `cancelled`. */
    val status: String,
    val createdAt: String? = null,
)

/** The created (or replayed) application and whether the API recognised a retry. */
data class ExternalCreatedApplication(
    val application: ExternalApplication,
    val idempotentReplay: Boolean,
)

/**
 * POST /applications.
 *
 * Wire names are pinned with @get:JsonProperty: a Kotlin property called `aName` has the
 * getter `getAName`, which bean naming would otherwise serialise as `aname`.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExternalCreateApplicationRequest(
    val clientApplicationId: UUID,
    val clientUserId: String,
    val courseId: Int,
    @get:JsonProperty("aName") val aName: String,
    @get:JsonProperty("aBirthdate") val aBirthdate: String,
    @get:JsonProperty("aEmail") val aEmail: String,
    @get:JsonProperty("aHandy") val aHandy: String,
    @get:JsonProperty("aGender") val aGender: String? = null,
    @get:JsonProperty("aBaptized") val aBaptized: String? = null,
    @get:JsonProperty("aBaptizeType") val aBaptizeType: String? = null,
    @get:JsonProperty("aResidence") val aResidence: String? = null,
    @get:JsonProperty("aGroup") val aGroup: String? = null,
    @get:JsonProperty("aGyogu") val aGyogu: String? = null,
    @get:JsonProperty("aSoon") val aSoon: String? = null,
    @get:JsonProperty("aChildren") val aChildren: String? = null,
    @get:JsonProperty("aHistory") val aHistory: String? = null,
    @get:JsonProperty("aWaiting") val aWaiting: String? = null,
    @get:JsonProperty("aRunning") val aRunning: String? = null,
    @get:JsonProperty("aComment") val aComment: String? = null,
) {
    // Hand-written: the generated toString would put name, birth date, email and phone
    // number into any log line or exception message that renders the request.
    override fun toString(): String = "ExternalCreateApplicationRequest(courseId=$courseId, clientApplicationId=$clientApplicationId)"
}
