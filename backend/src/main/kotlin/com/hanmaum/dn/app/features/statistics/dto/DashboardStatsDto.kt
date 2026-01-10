package com.hanmaum.dn.app.features.statistics.dto

data class DashboardStatsDto(
    val totalMembers: Long,
    val newMembersYtd: Long, // year to date (dieses jahr)
    val averageAge: Double,

    val cityDistribution: List<ChartDataDto>,
    val ageDistribution: List<ChartDataDto>,
    val genderDistribution: List<ChartDataDto>
)