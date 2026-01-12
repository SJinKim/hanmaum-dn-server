package com.hanmaum.dn.app.features.carpool.api.v1.dto

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// Was die App anzeigt
data class CarDto(
    val id: Long,
    val driverName: String, // Vorname + Nachname
    val carName: String?,
    val maxSeats: Int,
    val currentPassengers: Int,
    val departureLocation: String?,
    val departureTime: LocalDateTime?,

    // UI Helfer
    val isFull: Boolean,
    val isJoinedByMe: Boolean // True, wenn der User, der anfragt, hier drin sitzt
)

// Auto erstellen (Admin oder Fahrer)
data class CreateCarRequest(
    val driverMemberId: String, // Wir nutzen hier die PUBLIC_ID (UUID) des Fahrers!
    val sessionDate: LocalDate,
    val name: String?,
    val maxSeats: Int,
    val departureLocation: String?,
    val departureTime: LocalDateTime?
)
