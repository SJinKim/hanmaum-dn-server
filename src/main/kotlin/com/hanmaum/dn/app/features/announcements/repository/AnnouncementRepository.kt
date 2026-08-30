package com.hanmaum.dn.app.features.announcements.repository

import com.hanmaum.dn.app.features.announcements.domain.Announcement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

interface AnnouncementRepository : JpaRepository<Announcement, Long> {
    fun findByPublicIdAndDeleteEntryAtIsNull(publicId: UUID): Optional<Announcement>

    @Query(
        "SELECT a FROM Announcement a WHERE a.deleteEntryAt IS NULL AND a.startAt <= :now AND (a.endAt IS NULL OR a.endAt >= :now) ORDER BY a.isPinned DESC, a.startAt DESC",
    )
    fun findActiveAnnouncements(now: OffsetDateTime): List<Announcement>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE Announcement a
        SET a.viewCount = a.viewCount + 1
        WHERE a.publicId = :publicId
          AND a.deleteEntryAt IS NULL
          AND a.startAt <= :now
          AND (a.endAt IS NULL OR a.endAt >= :now)
        """,
    )
    fun incrementActiveViewCount(
        @Param("publicId") publicId: UUID,
        @Param("now") now: OffsetDateTime,
    ): Int

    // Admin dashboard: all announcements not scheduled for deletion, regardless of start/end window.
    @Query(
        "SELECT a FROM Announcement a WHERE a.deleteEntryAt IS NULL ORDER BY a.createdAt DESC",
    )
    fun findAllNotDeleted(): List<Announcement>

    // Admin trash sets delete_entry_at = click time; rows are purged 30 days later.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        """
        DELETE FROM Announcement a
        WHERE a.deleteEntryAt IS NOT NULL
          AND a.deleteEntryAt <= :cutoff
        """,
    )
    fun hardDeleteExpired(
        @Param("cutoff") cutoff: Instant,
    ): Int
}
