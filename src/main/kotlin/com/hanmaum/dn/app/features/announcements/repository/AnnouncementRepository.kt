package com.hanmaum.dn.app.features.announcements.repository

import com.hanmaum.dn.app.features.announcements.domain.Announcement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime

interface AnnouncementRepository : JpaRepository<Announcement, Long> {
    // Hol alle, die schon gestartet sind (startAt <= now)
    // UND (entweder kein Enddatum haben ODER das Enddatum noch in der Zukunft liegt)
    // Sortiert: Gepinnte zuerst, dann neueste
    @Query(
        "SELECT a FROM Announcement a WHERE a.deletedAt IS NULL AND a.startAt <= :now AND (a.endAt IS NULL OR a.endAt >= :now) ORDER BY a.isPinned DESC, a.startAt DESC",
    )
    fun findActiveAnnouncements(now: OffsetDateTime): List<Announcement>

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        """
        DELETE FROM Announcement a
        WHERE a.deleteEntryAt <= :now
          AND a.deletedAt IS NOT NULL
        """,
    )
    fun hardDeleteExpired(
        @Param("now") now: Instant,
    ): Int
}
