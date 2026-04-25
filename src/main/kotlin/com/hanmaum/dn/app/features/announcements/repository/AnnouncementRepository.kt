package com.hanmaum.dn.app.features.announcements.repository

import com.hanmaum.dn.app.features.announcements.domain.Announcement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface AnnouncementRepository : JpaRepository<Announcement, Long> {
    // Hol alle, die schon gestartet sind (startAt <= now)
    // UND (entweder kein Enddatum haben ODER das Enddatum noch in der Zukunft liegt)
    // Sortiert: Gepinnte zuerst, dann neueste
    @Query(
        "SELECT a FROM Announcement a WHERE a.deletedAt IS NULL AND a.startAt <= :now AND (a.endAt IS NULL OR a.endAt >= :now) ORDER BY a.isPinned DESC, a.startAt DESC",
    )
    fun findActiveAnnouncements(now: LocalDateTime): List<Announcement>
}
