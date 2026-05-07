package com.hanmaum.dn.app.features.statistics.api.v1

import com.hanmaum.dn.app.features.statistics.api.v1.dto.DashboardStatsDto
import com.hanmaum.dn.app.features.statistics.service.StatisticsService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/statistics")
class StatisticsController(
    private val statisticsService: StatisticsService,
) {
    @GetMapping("/dashboard")
    fun getDashboardStatistics(): ResponseEntity<DashboardStatsDto> {
        val stats = statisticsService.getDashboardStats()
        return ResponseEntity(stats, HttpStatus.OK)
    }
}
