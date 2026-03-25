package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceLogDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CheckInRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CheckInStatusResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import com.hanmaum.dn.app.features.attendance.repository.AttendanceDefinitionRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class AttendanceService(
    private val defRepo: AttendanceDefinitionRepository,
    private val logRepo: AttendanceLogRepository,
    private val memberRepo: MemberRepository,
) {
    // 1. Status prüfen (Für die App-Startseite)
    @Transactional(readOnly = true)
    fun getCheckInStatus(memberPublicId: String): CheckInStatusResponse {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val currentTime = now.toLocalTime()
        val currentDay = now.dayOfWeek

        // A. Gibt es eine aktive Definition für JETZT?
        val activeDefinition =
            defRepo
                .findByDayOfWeekAndActiveTrue(currentDay)
                .firstOrNull { def ->
                    currentTime.isAfter(def.startTime) && currentTime.isBefore(def.endTime)
                }

        // B. Hat der User heute schon eingecheckt?
        val member =
            memberRepo
                .findByPublicId(UUID.fromString(memberPublicId))
                .orElseThrow { EntityNotFoundException("member not found") }

        val alreadyCheckedIn =
            logRepo.existsByMemberIdAndDateAndCategory(
                member.id!!,
                today,
                "SUNDAY_SERVICE",
            )

        // C. Entscheidung
        return if (activeDefinition != null) {
            CheckInStatusResponse(
                isCheckInActive = !alreadyCheckedIn, // Aktiv nur, wenn noch nicht eingecheckt
                activeDefinitionTitle = activeDefinition.title,
                alreadyCheckedIn = alreadyCheckedIn,
                message = if (alreadyCheckedIn) "Du bist bereits eingecheckt ✅" else "Check-in offen! 👋",
            )
        } else {
            CheckInStatusResponse(
                isCheckInActive = false,
                activeDefinitionTitle = null,
                alreadyCheckedIn = alreadyCheckedIn,
                message = "Kein Check-in Zeitraum aktiv 💤",
            )
        }
    }

    // 2. Check-in durchführen
    @Transactional
    fun checkIn(req: CheckInRequest) {
        // Logik wiederholen (Security First: Vertraue nie dem Frontend allein)
        val status = getCheckInStatus(req.memberId)

        if (status.alreadyCheckedIn) {
            throw IllegalStateException("Already checked in today")
        }
        if (!status.isCheckInActive && status.activeDefinitionTitle == null) {
            // Streng genommen müsste man hier Exception werfen,
            // aber für Kulanz könnte man es auch durchlassen, wenn es nur um Sekunden geht.
            // Wir werfen hier strikt:
            throw IllegalStateException("Check-in is currently closed")
        }

        val member = memberRepo.findByPublicId(UUID.fromString(req.memberId)).get() // Existiert sicher, da oben geprüft

        val log =
            AttendanceLog(
                member = member,
                date = LocalDateTime.now().toLocalDate(),
                category = "SUNDAY_SERVICE",
                status = "PRESENT",
            )
        logRepo.save(log)
    }

    // 3. Admin: Regel erstellen
    @Transactional
    fun createDefinition(req: CreateDefinitionRequest): Long {
        val def =
            AttendanceDefinition(
                dayOfWeek = req.dayOfWeek,
                startTime = req.startTime,
                endTime = req.endTime,
                title = req.title,
            )
        return defRepo.save(def).id!!
    }

    // 4. Historie holen
    @Transactional(readOnly = true)
    fun getMyHistory(memberPublicId: String): List<AttendanceLogDto> {
        val member =
            memberRepo
                .findByPublicId(UUID.fromString(memberPublicId))
                .orElseThrow { EntityNotFoundException("Member not found") }

        return logRepo.findAllByMemberIdOrderByDateDesc(member.id!!).map { log ->
            AttendanceLogDto(
                id = log.id!!,
                date = log.date,
                category = log.category,
                status = log.status,
                // created_at kommt aus BaseEntity, evtl. mappen oder null wenn nicht verfügbar
                checkInTime = null, // TODO: BaseEntity createdAt getter nutzen
            )
        }
    }
}
