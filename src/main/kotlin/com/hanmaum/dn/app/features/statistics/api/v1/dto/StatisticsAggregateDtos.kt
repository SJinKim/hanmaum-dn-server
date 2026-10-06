package com.hanmaum.dn.app.features.statistics.api.v1.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalTime
import java.util.UUID

@Schema(description = "One line of the 성장 추이 chart: members per bucket of one year")
data class TrendSeriesDto(
    val year: Int,
    @field:Schema(description = "Members on the last day of each bucket; buckets in the future are left out")
    val points: List<ChartDataDto>,
)

@Schema(description = "Check-ins of one active service, this week against last week (Monday to Sunday)")
data class ServiceAttendanceDto(
    val definitionPublicId: UUID,
    val title: String,
    val windowStart: LocalTime,
    val thisWeek: Long,
    val lastWeek: Long,
)

@Schema(description = "Members currently in one 양육 course (enrolled or in progress)")
data class TrainingStageDto(
    val code: String,
    val name: String,
    val count: Long,
)
