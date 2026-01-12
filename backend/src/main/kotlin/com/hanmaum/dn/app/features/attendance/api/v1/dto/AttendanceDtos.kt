package com.hanmaum.dn.app.features.attendance.api.v1.dto

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// Antwort für die App: "Darf ich den Button zeigen?"
data class CheckInStatusResponse(
    val isCheckInActive: Boolean, // True = Button Grün, False = Grau
    val activeDefinitionTitle: String?, // z.B. "Sonntagsgottesdienst"
    val alreadyCheckedIn: Boolean, // True = User hat heute schon gedrückt
    val message: String? // Info für den User (z.B. "Check-in startet um 11:00")
)

// Request: User drückt den Button
data class CheckInRequest(
    val memberId: String, // Public UUID
    // Optional: Geo-Location könnte hier später rein
)

// Historie für den User
data class AttendanceLogDto(
    val id: Long,
    val date: LocalDate,
    val category: String,
    val status: String,
    val checkInTime: LocalTime?
)

// Admin: Definition anlegen
data class CreateDefinitionRequest(
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val title: String
)