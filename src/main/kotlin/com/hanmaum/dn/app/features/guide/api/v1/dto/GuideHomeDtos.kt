package com.hanmaum.dn.app.features.guide.api.v1.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Completeness refers to canonical service totals, not availability of historical room details. */
enum class GuideHomeStatus { EMPTY, PARTIAL, COMPLETE }

data class GuideHomeMetrics(
    @field:Schema(types = ["string", "null"], format = "date") val reportDate: LocalDate?,
    @field:Schema(types = ["integer", "null"], format = "int64") val totalCount: Long?,
    @field:Schema(types = ["string", "null"], format = "date") val referenceDate: LocalDate?,
    @field:Schema(types = ["integer", "null"], format = "int64") val referenceTotal: Long?,
    @field:Schema(types = ["integer", "null"], format = "int64") val absoluteChange: Long?,
    @field:Schema(types = ["number", "null"], description = "DECIMAL128; round only for display.") val percentChange: BigDecimal?,
    @field:Schema(
        types = ["string", "null"],
        description = "Null when computable; otherwise NO_CURRENT_DATA, NO_BASELINE_DATA or ZERO_BASELINE.",
    ) val percentReason: String?,
    @field:Schema(types = ["number", "null"], description = "Mean of the latest up to four complete Sunday reports.")
    val meanLastFour: BigDecimal?,
    val meanReportCount: Int,
    @field:Schema(types = ["string", "null"], format = "date") val meanFrom: LocalDate?,
    @field:Schema(types = ["string", "null"], format = "date") val meanTo: LocalDate?,
)

data class GuideHomePoint(
    val serviceDate: LocalDate,
    @field:Schema(types = ["integer", "null"], format = "int32") val service1Count: Int?,
    @field:Schema(types = ["integer", "null"], format = "int32") val service2Count: Int?,
    @field:Schema(types = ["integer", "null"], format = "int32") val service3Count: Int?,
    @field:Schema(types = ["integer", "null"], format = "int64") val totalCount: Long?,
)

data class GuideHomeShare(
    val serviceNumber: Int,
    @field:Schema(types = ["integer", "null"], format = "int32") val count: Int?,
    @field:Schema(types = ["number", "null"]) val percent: BigDecimal?,
)

data class GuideHomeResponse(
    val asOf: Instant,
    val timeZone: String,
    val currentSunday: LocalDate,
    @field:Schema(description = "Opaque version of this consistent read. Changes when included reports or their versions change.")
    val snapshotVersion: String,
    val status: GuideHomeStatus,
    val waitingForCurrentSunday: Boolean,
    @field:Schema(
        types = ["object", "null"],
        schemaResolution = Schema.SchemaResolution.INLINE,
        description =
            "Latest confirmed report through current Sunday; serviceDate is the title date. " +
                "Detailed room membership is historical.",
    ) val attendance: GuideDetailsResponse?,
    @field:Schema(
        types = ["object", "null"],
        schemaResolution = Schema.SchemaResolution.INLINE,
        description = "Existing current-Sunday record, including a blank draft. Resume this UUID instead of creating a duplicate.",
    ) val currentSundayReport: WorshipCountResponse?,
    val lastFourReports: List<WorshipCountResponse>,
    val metrics: GuideHomeMetrics,
    @field:Schema(description = "Exactly eight calendar Sundays ending currentSunday; missing values remain null.")
    val timeline: List<GuideHomePoint>,
    @field:Schema(types = ["string", "null"], format = "date") val sharesReportDate: LocalDate?,
    val shares: List<GuideHomeShare>,
    @field:Schema(types = ["string", "null"], description = "NO_COMPLETE_REPORT or ZERO_TOTAL when shares cannot be computed.")
    val sharesReason: String?,
)
