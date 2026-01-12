package com.hanmaum.dn.app.features.dishwashing.api

import org.springframework.web.bind.annotation.RequestMapping
import com.hanmaum.dn.app.features.dishwashing.api.v1.dto.CreateDishwashingRequest
import com.hanmaum.dn.app.features.dishwashing.api.v1.dto.DishwashingDayDto
import com.hanmaum.dn.app.features.dishwashing.service.DishwashingService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/dishwashing")
class DishwashingController(private val service: DishwashingService) {

    // Für die App (User)
    @GetMapping
    fun getSchedule(): List<DishwashingDayDto> {
        return service.getUpcomingSchedule()
    }

    // Für Admin (Erstellen / Überschreiben für einen Tag)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createSchedule(@RequestBody req: CreateDishwashingRequest) {
        service.createSchedule(req)
    }

    // Für Admin (Löschen)
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSchedule(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ) {
        service.deleteScheduleForDate(date)
    }
}