package com.hanmaum.dn.app.features.statistics.api.v1.dto

import io.swagger.v3.oas.annotations.media.Schema

data class DashboardStatsDto(
    val totalMembers: Long,
    val newMembersYtd: Long, // year to date (dieses jahr)
    val averageAge: Double,
    val cityDistribution: List<ChartDataDto>,
    val ageDistribution: List<ChartDataDto>,
    val genderDistribution: List<ChartDataDto>,
    @field:Schema(description = "The period the aggregates below were computed for", allowableValues = ["30d", "quarter", "year"])
    val period: String,
    @field:Schema(description = "Current year first, then the same buckets one year earlier")
    val growthTrend: List<TrendSeriesDto>,
    @field:Schema(description = "Attendance rate in percent per bucket: check-ins / (members × services held)")
    val attendanceRate: List<ChartDataDto>,
    val serviceAttendance: List<ServiceAttendanceDto>,
    @field:Schema(description = "Distinct attendees per division of the 순 they checked in with, within the period")
    val divisionAttendance: List<ChartDataDto>,
    val trainingStages: List<TrainingStageDto>,
    @field:Schema(description = "Members currently serving per active ministry, largest first")
    val ministryHeadcount: List<ChartDataDto>,
)
