package com.hanmaum.dn.app.features.guide.api.v1.dto

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import java.time.LocalDate
import java.util.UUID

class GuideVersionDeserializer : ValueDeserializer<Long>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): Long {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return context.reportInputMismatch(Long::class.java, "Expected version must be an integer JSON number.")
        }
        val value = parser.bigIntegerValue
        if (value.signum() < 0 || value > Long.MAX_VALUE.toBigInteger()) {
            return context.reportInputMismatch(Long::class.java, "Invalid expected version.")
        }
        return value.toLong()
    }
}

/** Reject computed/unknown fields instead of silently clearing base counts on a mismatched payload. */
abstract class GuideStrictRequest {
    @JsonAnySetter
    fun rejectUnknown(
        field: String,
        ignoredValue: JsonNode?,
    ): Unit = throw IllegalArgumentException("Unknown or read-only field: $field")
}

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
data class GuideDetailEntryRequest(
    @field:Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        description = "Counting area UUID from the report snapshot or current configuration on create.",
    )
    val areaPublicId: UUID,
    @field:JsonDeserialize(using = GuideCountDeserializer::class)
    @param:JsonProperty(required = true)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "3") val serviceNumber: Int,
    @field:JsonDeserialize(using = GuideCountDeserializer::class)
    @field:Schema(types = ["integer", "null"], minimum = "0", maximum = "2147483647") val adultCount: Int? = null,
    @field:JsonDeserialize(using = GuideCountDeserializer::class)
    @field:Schema(types = ["integer", "null"], minimum = "0", maximum = "2147483647") val schoolCount: Int? = null,
    @field:JsonDeserialize(using = GuideCountDeserializer::class)
    @field:Schema(
        types = ["integer", "null"],
        minimum = "0",
        maximum = "2147483647",
        description = "Only service 3; omitted/null on services 1 and 2.",
    )
    val youthCount: Int? = null,
) : GuideStrictRequest()

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
data class GuideDetailsCreateRequest(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val serviceDate: LocalDate,
    val notes: String? = null,
    @field:Valid
    @field:Schema(description = "Subset allowed; omitted cells/rows become unknown. Three rows per snapshotted area, never implicit zero.")
    val entries: List<@NotNull GuideDetailEntryRequest> = emptyList(),
) : GuideStrictRequest()

/** Explicit comparison of the old canonical values, in addition to the expected version. */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
data class GuideSummaryConfirmation(
    @param:JsonProperty(required = true) @field:JsonDeserialize(using = GuideCountDeserializer::class)
    @field:Schema(types = ["integer", "null"], requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "2147483647")
    val service1Count: Int?,
    @param:JsonProperty(required = true) @field:JsonDeserialize(using = GuideCountDeserializer::class)
    @field:Schema(types = ["integer", "null"], requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "2147483647")
    val service2Count: Int?,
    @param:JsonProperty(required = true) @field:JsonDeserialize(using = GuideCountDeserializer::class)
    @field:Schema(types = ["integer", "null"], requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0", maximum = "2147483647")
    val service3Count: Int?,
) : GuideStrictRequest()

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
data class GuideDetailsUpdateRequest(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val serviceDate: LocalDate,
    @param:JsonProperty(required = true) @field:Schema(types = ["string", "null"], requiredMode = Schema.RequiredMode.REQUIRED)
    val notes: String?,
    @param:JsonProperty(required = true) @field:JsonDeserialize(using = GuideVersionDeserializer::class)
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") val version: Long,
    @param:JsonProperty(required = true) @field:Valid
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Full replacement; omitted rows become unknown.")
    val entries: List<@NotNull GuideDetailEntryRequest>,
    @field:Schema(
        types = ["object", "null"],
        schemaResolution = Schema.SchemaResolution.INLINE,
        description = "Required only to explicitly convert SUMMARY_ONLY; must match current canonical counts.",
    )
    val expectedSummary: GuideSummaryConfirmation? = null,
) : GuideStrictRequest()

data class GuideCountTotals(
    @field:Schema(
        types = ["integer", "null"],
        minimum = "0",
        maximum = "6442450941",
        description = "Adults excluding youth.",
    ) val adultCount: Long?,
    @field:Schema(
        types = ["integer", "null"],
        minimum = "0",
        maximum = "2147483647",
        description = "Youth from service 3 only; null when not applicable or unknown.",
    ) val youthCount: Long?,
    @field:Schema(
        types = ["integer", "null"],
        minimum = "0",
        maximum = "6442450941",
        description = "Adults plus youth, counted exactly once.",
    ) val compatibleAdultCount: Long?,
    @field:Schema(types = ["integer", "null"], minimum = "0", maximum = "6442450941") val schoolCount: Long?,
    @field:Schema(types = ["integer", "null"], minimum = "0", maximum = "6442450941") val totalCount: Long?,
    val complete: Boolean,
)

data class GuideDetailEntryResponse(
    val areaPublicId: UUID,
    val serviceNumber: Int,
    val counts: GuideCountTotals,
)

data class GuideRoomTotals(
    val areaPublicId: UUID,
    val roomPublicId: UUID,
    val roomName: String,
    val label: String,
    val displayOrder: Int,
    val counts: GuideCountTotals,
)

data class GuideServiceTotals(
    val serviceNumber: Int,
    @field:Schema(description = "Every sum for this single service is at most 2147483647; missing required base values make it null.")
    val counts: GuideCountTotals,
)

data class GuideDetailsResponse(
    val report: WorshipCountResponse,
    val entries: List<GuideDetailEntryResponse>,
    val rooms: List<GuideRoomTotals>,
    val services: List<GuideServiceTotals>,
    @field:Schema(
        types = ["object", "null"],
        schemaResolution = Schema.SchemaResolution.INLINE,
        description = "No invented category distribution for SUMMARY_ONLY reports.",
    )
    val totals: GuideCountTotals?,
)

data class GuideValidationErrors(
    val fieldErrors: Map<String, String>,
)
