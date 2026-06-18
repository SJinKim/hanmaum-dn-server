package com.hanmaum.dn.app.features.events.repository

import com.hanmaum.dn.app.features.events.domain.EventRsvp
import com.hanmaum.dn.app.features.events.domain.EventRsvpLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

@Repository
interface EventRsvpRepository : JpaRepository<EventRsvp, Long> {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<EventRsvp>

    @Query(
        """
        SELECT r FROM EventRsvp r
        WHERE r.isActive = true
          AND r.windowStart <= :now
          AND r.windowEnd > :now
          AND r.deletedAt IS NULL
        ORDER BY r.windowStart ASC
        """,
    )
    fun findActiveNow(
        @Param("now") now: OffsetDateTime,
    ): List<EventRsvp>

    @Query(
        """
        SELECT r FROM EventRsvp r
        WHERE r.deletedAt IS NULL
        ORDER BY r.windowStart DESC
        """,
    )
    fun findAllNotDeleted(): List<EventRsvp>
}

@Repository
interface EventRsvpLogRepository : JpaRepository<EventRsvpLog, Long> {
    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO event_rsvp_logs (
                public_id, event_rsvp_id, member_id, group_id_at_rsvp, checked_in_at, created_at
            )
            VALUES (
                :publicId, :eventRsvpId, :memberId, :groupId, :checkedInAt, CURRENT_TIMESTAMP
            )
            ON CONFLICT ON CONSTRAINT uq_event_rsvp_log DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("publicId") publicId: UUID,
        @Param("eventRsvpId") eventRsvpId: Long,
        @Param("memberId") memberId: Long,
        @Param("groupId") groupId: Long?,
        @Param("checkedInAt") checkedInAt: Instant,
    ): Int

    @Query(
        """
        SELECT l FROM EventRsvpLog l
        JOIN FETCH l.member
        LEFT JOIN FETCH l.groupAtRsvp
        WHERE l.eventRsvp.id = :eventRsvpId
          AND l.deletedAt IS NULL
        ORDER BY l.checkedInAt ASC
        """,
    )
    fun findAttendeesWithDetails(
        @Param("eventRsvpId") eventRsvpId: Long,
    ): List<EventRsvpLog>
}
