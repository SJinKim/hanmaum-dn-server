package com.hanmaum.dn.app.features.groups.api.v1.dto

import java.time.OffsetDateTime

// --- RESPONSE (Anzeige) ---
// Übersicht für die Liste
data class GroupMeetingDto(
    val id: Long,
    val groupName: String,
    val meetingTime: OffsetDateTime,
    val location: String,
    val description: String,
    val attendanceCount: Int? = 0, // Wie viele waren da?
)

// Detailansicht (Nur für eigene Gruppe oder Pastor!)
data class MeetingDetailDto(
    val id: Long,
    val groupName: String,
    val meetingTime: OffsetDateTime,
    val location: String,
    // Die Liste der Teilnehmer & Gebete
    val attendees: List<AttendanceEntryDto>,
)

data class AttendanceEntryDto(
    val memberName: String,
    val memberId: String, // Public UUID
    val status: String,
    val prayerRequest: String?, // Das Gebetsanliegen
)

// --- REQUEST (Eingabe) ---

// Admin: Erstellt das Treffen (Ort & Zeit)
data class CreateMeetingRequest(
    val groupId: Long,
    val meetingTime: OffsetDateTime,
    val location: String,
    val description: String = "순모임",
)

// Leader: Reicht den Bericht ein (Anwesenheit & Gebete)
data class SubmitMeetingReportRequest(
    // Liste aller Mitglieder der Gruppe mit ihrem Status/Gebet
    val entries: List<ReportEntry>,
)

data class ReportEntry(
    val memberId: String, // Public UUID des Mitglieds
    val isPresent: Boolean,
    val prayerRequest: String?,
)
