package com.hanmaum.dn.app.features.dishwashing.api.v1.dto

import java.time.LocalDate

// RESPONSE: Ein Datum, mehrere Gruppen
data class DishwashingDayDto(
    val date: LocalDate,
    val groupNames: List<String>,
    val note: String?,
)

// REQUEST: Admin legt fest
data class CreateDishwashingRequest(
    val date: LocalDate,
    val groupIds: List<Long>, // Admin sendet hier die IDs der 2 Gruppen
    val note: String? = null,
)
