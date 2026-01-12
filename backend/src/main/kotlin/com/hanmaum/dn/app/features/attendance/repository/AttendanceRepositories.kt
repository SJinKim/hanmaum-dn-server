package com.hanmaum.dn.app.features.attendance.repository

import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.DayOfWeek
import java.time.LocalDate

@Repository
interface AttendanceDefinitionRepository : JpaRepository<AttendanceDefinition, Long> {
    // Finde alle aktiven Regeln für einen bestimmten Wochentag
    fun findByDayOfWeekAndIsActiveTrue(dayOfWeek: DayOfWeek): List<AttendanceDefinition>
}

@Repository
interface AttendanceLogRepository : JpaRepository<AttendanceLog, Long> {
    // Prüfen, ob Member an diesem Datum schon eingeloggt ist
    fun existsByMemberIdAndDateAndCategory(memberId: Long, date: LocalDate, category: String): Boolean

    // Historie für einen Member
    fun findAllByMemberIdOrderByDateDesc(memberId: Long): List<AttendanceLog>
}