package com.hanmaum.dn.app.features.guide.api.v1.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

private const val GUIDE_REASON_DESCRIPTION =
    "Null when computable; otherwise NO_COMPLETE_REPORTS, ZERO_TOTAL, NO_CURRENT_DATA, " +
        "NO_BASELINE_DATA or ZERO_BASELINE."

/** Stable presentation order: three services, then their combined total. */
enum class GuideStatisticKey { SERVICE_1, SERVICE_2, SERVICE_3, TOTAL }

enum class GuideStatisticNullReason { NO_COMPLETE_REPORTS, ZERO_TOTAL, NO_CURRENT_DATA, NO_BASELINE_DATA, ZERO_BASELINE }

data class GuideStatisticMetric(
    val key: GuideStatisticKey,
    @field:Schema(types = ["integer", "null"], format = "int64", minimum = "0")
    val sum: Long?,
    @field:Schema(
        types = ["number", "null"],
        description = "Mean of complete reports. DECIMAL128 (34 significant digits); client rounds for display.",
    )
    val average: BigDecimal?,
    @field:Schema(
        implementation = String::class,
        types = ["string", "null"],
        description = GUIDE_REASON_DESCRIPTION,
    )
    val averageReason: GuideStatisticNullReason?,
    @field:Schema(
        types = ["number", "null"],
        description = "Service sum / same-cohort total * 100. TOTAL is 100 only for a positive total.",
    )
    val sharePercent: BigDecimal?,
    @field:Schema(
        implementation = String::class,
        types = ["string", "null"],
        description = GUIDE_REASON_DESCRIPTION,
    )
    val shareReason: GuideStatisticNullReason?,
)

data class GuideStatisticPeriod(
    val from: LocalDate,
    val to: LocalDate,
    @field:Schema(types = ["string", "null"], format = "date")
    val firstReportDate: LocalDate?,
    @field:Schema(types = ["string", "null"], format = "date")
    val lastReportDate: LocalDate?,
    @field:Schema(types = ["string", "null"], format = "date")
    val firstCompleteDate: LocalDate?,
    @field:Schema(types = ["string", "null"], format = "date")
    val lastCompleteDate: LocalDate?,
    val reportCount: Int,
    val completeCount: Int,
    val incompleteCount: Int,
    val metrics: List<GuideStatisticMetric>,
)

data class GuideStatisticChange(
    val key: GuideStatisticKey,
    @field:Schema(types = ["number", "null"], description = "Difference of current and baseline means, DECIMAL128.")
    val absoluteChange: BigDecimal?,
    @field:Schema(
        implementation = String::class,
        types = ["string", "null"],
        description = GUIDE_REASON_DESCRIPTION,
    )
    val absoluteReason: GuideStatisticNullReason?,
    @field:Schema(
        types = ["number", "null"],
        description = "(current mean / baseline mean - 1) * 100. Null for missing/zero baseline; DECIMAL128.",
    )
    val percentChange: BigDecimal?,
    @field:Schema(
        implementation = String::class,
        types = ["string", "null"],
        description = GUIDE_REASON_DESCRIPTION,
    )
    val percentReason: GuideStatisticNullReason?,
)

data class GuideStatisticPoint(
    val serviceDate: LocalDate,
    @field:Schema(types = ["integer", "null"], format = "int32", minimum = "0", maximum = "2147483647")
    val service1Count: Int?,
    @field:Schema(types = ["integer", "null"], format = "int32", minimum = "0", maximum = "2147483647")
    val service2Count: Int?,
    @field:Schema(types = ["integer", "null"], format = "int32", minimum = "0", maximum = "2147483647")
    val service3Count: Int?,
    @field:Schema(types = ["integer", "null"], format = "int64", minimum = "0", maximum = "6442450941")
    val totalCount: Long?,
    val complete: Boolean,
)

data class GuideStatisticsResponse(
    val year: Int,
    val asOf: LocalDate,
    val comparisonYear: Int,
    val timeZone: String,
    val current: GuideStatisticPeriod,
    val previousSamePeriod: GuideStatisticPeriod,
    val previousFullYear: GuideStatisticPeriod,
    val changesFromSamePeriod: List<GuideStatisticChange>,
    val changesFromFullYear: List<GuideStatisticChange>,
    @field:Schema(
        description = "Ascending reported dates only. Partial values remain null; missing weeks are not filled with zeros.",
    )
    val timeline: List<GuideStatisticPoint>,
)
