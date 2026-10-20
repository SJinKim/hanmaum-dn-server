package com.hanmaum.dn.app.features.guide.api.v1.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.PositiveOrZero
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.annotation.JsonDeserialize
import java.time.LocalDate
import java.util.UUID

/** Only integer JSON tokens; no decimal truncation, numeric strings or Boolean coercion. */
class GuideCountDeserializer : ValueDeserializer<Int>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): Int {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return context.reportInputMismatch(Int::class.java, "Count must be a nonnegative integer JSON number.")
        }
        val value = parser.bigIntegerValue
        if (value.signum() < 0 || value > Int.MAX_VALUE.toBigInteger()) {
            return context.reportInputMismatch(Int::class.java, "Count must be between 0 and 2147483647.")
        }
        return value.toInt()
    }
}

/** Full replacement of the three counts and notes; null is unrecorded, never zero. */
data class WorshipCountRequest(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    val serviceDate: LocalDate,
    @field:JsonDeserialize(using = GuideCountDeserializer::class)
    @field:Schema(types = ["integer", "null"], minimum = "0", maximum = "2147483647", description = "1부 count; null means unrecorded.")
    val service1Count: Int? = null,
    @field:JsonDeserialize(using = GuideCountDeserializer::class)
    @field:Schema(types = ["integer", "null"], minimum = "0", maximum = "2147483647", description = "2부 count; null means unrecorded.")
    val service2Count: Int? = null,
    @field:JsonDeserialize(using = GuideCountDeserializer::class)
    @field:Schema(types = ["integer", "null"], minimum = "0", maximum = "2147483647", description = "3부 count; null means unrecorded.")
    val service3Count: Int? = null,
    @field:Schema(types = ["string", "null"], description = "특이사항; original notes and line breaks retained.")
    val notes: String? = null,
    @field:PositiveOrZero
    @field:Schema(types = ["integer", "null"], description = "Required expected version for PATCH; omitted on POST.")
    val version: Long? = null,
)

data class WorshipCountResponse(
    val publicId: UUID,
    val serviceDate: LocalDate,
    @field:Schema(types = ["integer", "null"], minimum = "0", maximum = "2147483647") val service1Count: Int?,
    @field:Schema(types = ["integer", "null"], minimum = "0", maximum = "2147483647") val service2Count: Int?,
    @field:Schema(types = ["integer", "null"], minimum = "0", maximum = "2147483647") val service3Count: Int?,
    @field:Schema(
        types = ["integer", "null"],
        minimum = "0",
        maximum = "6442450941",
        description = "Derived sum as int64; null if any service is unrecorded.",
    )
    val totalCount: Long?,
    @field:Schema(
        types = ["integer", "null"],
        minimum = "-6442450941",
        maximum = "6442450941",
        description = "Difference to exactly seven days earlier; null if either report is incomplete or missing.",
    )
    val previousWeekDelta: Long?,
    @field:Schema(types = ["string", "null"]) val notes: String?,
    val version: Long,
    @field:Schema(
        allowableValues = ["SUMMARY_ONLY", "DETAILED"],
        description = "Explicit edit mode; detailed totals may only be changed via the detail endpoint.",
    )
    val reportMode: String = "SUMMARY_ONLY",
    val complete: Boolean,
)
