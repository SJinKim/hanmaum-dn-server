package com.hanmaum.dn.app.features.dishwashing.service

import com.hanmaum.dn.app.features.dishwashing.api.v1.dto.CreateDishwashingRequest
import com.hanmaum.dn.app.features.dishwashing.api.v1.dto.DishwashingDayDto
import com.hanmaum.dn.app.features.dishwashing.domain.DishwashingSchedule
import com.hanmaum.dn.app.features.dishwashing.repository.DishwashingRepository
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class DishwashingService (
    private val dishwashingRepository: DishwashingRepository,
    private val churchGroupRepository: ChurchGroupRepository
) {
    // 1. GET (Read Only für User)
    @Transactional(readOnly = true)
    fun getUpcomingSchedule(): List<DishwashingDayDto> {
        val today = LocalDate.now()
        // Hol alle Einträge (z.B. Zeile 1: 12.01->GrpA, Zeile 2: 12.01->GrpB)
        val rawEntries = dishwashingRepository.findAllByScheduledDateGreaterThanEqualOrderByScheduledDateAsc(today)

        // Gruppiere sie nach Datum! (Das ist Kotlin Magic 🪄)
        // Map<LocalDate, List<DishwashingSchedule>>
        val groupedByDate = rawEntries.groupBy { it.scheduledDate }

        // Mappe das in die schönen DTOs
        return groupedByDate.map { (date, entries) ->
            DishwashingDayDto(
                date = date,
                // Hole von allen Einträgen an diesem Tag den Gruppennamen
                groupNames = entries.map { it.group.name },
                // Nimm die Notiz vom ersten Eintrag (sollte eh gleich sein)
                note = entries.firstOrNull()?.note
            )
        }
    }

    // 2. CREATE (Admin Only)
    @Transactional
    fun createSchedule(req: CreateDishwashingRequest) {
        // Falls für das Datum schon was existiert: Erst löschen (Clean Slate Strategy)
        // Damit verhindern wir Dopplungen oder Reste, wenn der Admin korrigiert.
        dishwashingRepository.deleteAllByScheduledDate(req.date)

        // Die Gruppen laden
        val groups = churchGroupRepository.findAllById(req.groupIds)
        if (groups.size != req.groupIds.size) {
            throw EntityNotFoundException("One or more groups not found")
        }

        // Für jede Gruppe einen Eintrag speichern
        val schedules = groups.map { group ->
            DishwashingSchedule(
                scheduledDate = req.date,
                group = group,
                note = req.note
            )
        }
        dishwashingRepository.saveAll(schedules)
    }

    // 3. DELETE (Ganzen Tag löschen)
    @Transactional
    fun deleteScheduleForDate(date: LocalDate) {
        dishwashingRepository.deleteAllByScheduledDate(date)
    }
}
