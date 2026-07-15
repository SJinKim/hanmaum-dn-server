package com.hanmaum.dn.app.features.notifications.repository

import com.hanmaum.dn.app.features.notifications.domain.AppNotification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface AppNotificationRepository : JpaRepository<AppNotification, Long> {
    fun findAllByMemberIdOrderByCreatedAtDesc(
        memberId: Long,
        pageable: Pageable,
    ): Page<AppNotification>

    fun countByMemberIdAndSeenAtIsNull(memberId: Long): Long

    fun findByPublicIdAndMemberId(
        publicId: UUID,
        memberId: Long,
    ): AppNotification?

    @Modifying
    @Query("update AppNotification n set n.seenAt = :now where n.member.id = :memberId and n.seenAt is null")
    fun markAllSeen(
        @Param("memberId") memberId: Long,
        @Param("now") now: Instant,
    ): Int

    @Modifying
    @Query(
        "update AppNotification n set n.readAt = :now, " +
            "n.seenAt = coalesce(n.seenAt, :now) " +
            "where n.member.id = :memberId and n.readAt is null",
    )
    fun markAllRead(
        @Param("memberId") memberId: Long,
        @Param("now") now: Instant,
    ): Int
}
