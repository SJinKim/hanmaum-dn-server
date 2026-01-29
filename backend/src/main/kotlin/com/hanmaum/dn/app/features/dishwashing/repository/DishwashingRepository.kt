package com.hanmaum.dn.app.features.dishwashing.repository

import com.hanmaum.dn.app.features.dishwashing.domain.DishwashingSchedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface DishwashingRepository : JpaRepository<DishwashingSchedule, Long> {
    // Wir laden alle Pläne ab "Heute" (Vergangenheit interessiert beim Spülen meist nicht)
    // Sortiert nach Datum, damit die Liste in der App chronologisch ist.
    fun findAllByScheduledDateGreaterThanEqualOrderByScheduledDateAsc(date: LocalDate): List<DishwashingSchedule>

    // Zum Löschen/Prüfen
    fun deleteAllByScheduledDate(date: LocalDate)
}