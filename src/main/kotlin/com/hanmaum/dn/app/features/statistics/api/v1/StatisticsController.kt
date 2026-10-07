package com.hanmaum.dn.app.features.statistics.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.statistics.api.v1.dto.DashboardStatsDto
import com.hanmaum.dn.app.features.statistics.service.StatisticsPeriod
import com.hanmaum.dn.app.features.statistics.service.StatisticsService
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/statistics")
class StatisticsController(
    private val statisticsService: StatisticsService,
) {
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    fun getDashboardStatistics(
        @Parameter(
            description = "Time window of the trend, rate and division charts",
            schema = Schema(allowableValues = ["30d", "quarter", "year"], defaultValue = StatisticsPeriod.DEFAULT),
        )
        @RequestParam(defaultValue = StatisticsPeriod.DEFAULT)
        period: String,
    ): ResponseEntity<ApiResponse<DashboardStatsDto>> {
        val stats = statisticsService.getDashboardStats(StatisticsPeriod.fromParam(period))
        return ResponseEntity.ok(ApiResponse.success(data = stats))
    }
}
