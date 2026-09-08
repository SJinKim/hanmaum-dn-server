package com.hanmaum.dn.app.features.verses.repository

import com.hanmaum.dn.app.features.verses.domain.WeeklyVerse
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface WeeklyVerseRepository : JpaRepository<WeeklyVerse, Long> {
    fun findByWeekStartAndDeletedAtIsNull(weekStart: LocalDate): WeeklyVerse?
}
