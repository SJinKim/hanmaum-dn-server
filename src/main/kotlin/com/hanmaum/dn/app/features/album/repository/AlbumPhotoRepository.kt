package com.hanmaum.dn.app.features.album.repository

import com.hanmaum.dn.app.features.album.domain.AlbumPhoto
import com.hanmaum.dn.app.features.album.domain.PhotoStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/** Projection for the calendar tab: one row per day with count and a cover thumb key. */
interface CalendarDayProjection {
    fun getDay(): java.sql.Date

    fun getPhotoCount(): Long

    fun getCoverThumbKey(): String?
}

interface AlbumPhotoRepository : JpaRepository<AlbumPhoto, Long> {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): AlbumPhoto?

    fun findByAlbumIdAndStatusAndDeletedAtIsNullOrderByTakenAtDesc(
        albumId: Long,
        status: PhotoStatus,
        pageable: Pageable,
    ): Page<AlbumPhoto>

    fun countByAlbumIdAndStatusAndDeletedAtIsNull(
        albumId: Long,
        status: PhotoStatus,
    ): Long

    @Query(
        "SELECT p FROM AlbumPhoto p WHERE p.status = 'ACTIVE' AND p.deletedAt IS NULL " +
            "AND p.takenAt >= :from AND p.takenAt < :to ORDER BY p.takenAt ASC",
    )
    fun findActiveTakenBetween(
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): List<AlbumPhoto>

    @Query(
        value = """
            SELECT DATE(p.taken_at) AS "day",
                   COUNT(*) AS "photoCount",
                   (ARRAY_AGG(p.thumb_key ORDER BY p.taken_at ASC))[1] AS "coverThumbKey"
            FROM album_photos p
            WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL
              AND p.taken_at >= :from AND p.taken_at < :to
            GROUP BY DATE(p.taken_at)
            ORDER BY DATE(p.taken_at)
        """,
        nativeQuery = true,
    )
    fun aggregateCalendarDays(
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): List<CalendarDayProjection>

    fun findByStatusAndCreatedAtBefore(
        status: PhotoStatus,
        cutoff: Instant,
    ): List<AlbumPhoto>
}
