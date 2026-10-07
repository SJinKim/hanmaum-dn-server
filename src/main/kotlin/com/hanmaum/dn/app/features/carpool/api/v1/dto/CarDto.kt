package com.hanmaum.dn.app.features.carpool.api.v1.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// Was die App anzeigt
data class CarDto(
    val publicId: UUID,
    val driverName: String, // Vorname + Nachname
    val carName: String?,
    val maxSeats: Int,
    val currentPassengers: Int,
    val departureLocation: String?,
    val departureTime: LocalTime?,
    // UI Helfer
    @get:JsonProperty("isFull")
    val isFull: Boolean,
    @get:JsonProperty("isJoinedByMe")
    val isJoinedByMe: Boolean, // True, wenn der User, der anfragt, hier drin sitzt
)

// Auto erstellen. Fahrer ist der Aufrufer; nur ein Admin darf einen anderen Fahrer setzen.
data class CreateCarRequest(
    val driverMemberId: UUID? = null,
    val sessionDate: LocalDate,
    val name: String?,
    val maxSeats: Int,
    val departureLocation: String?,
    val departureTime: LocalTime?,
)
