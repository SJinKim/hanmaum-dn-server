package com.hanmaum.dn.app.features.newcomers.repository

import com.hanmaum.dn.app.features.newcomers.domain.NewcomerLifecycle
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface NewcomerProfileRepository : JpaRepository<NewcomerProfile, Long> {
    @EntityGraph(attributePaths = ["member", "caregiver", "assignedGroup"])
    fun findAllByDeletedAtIsNull(): List<NewcomerProfile>

    @EntityGraph(attributePaths = ["member", "caregiver", "assignedGroup"])
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<NewcomerProfile>

    fun findByMemberIdAndDeletedAtIsNull(memberId: Long): NewcomerProfile?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = ["member", "caregiver", "assignedGroup"])
    @Query("SELECT p FROM NewcomerProfile p WHERE p.publicId = :publicId AND p.deletedAt IS NULL")
    fun findForUpdate(
        @Param("publicId") publicId: UUID,
    ): NewcomerProfile?

    /** Members whose (non-deleted) newcomer profile is in [status]. Backs the members grid filter. */
    @Query("SELECT p.member.id FROM NewcomerProfile p WHERE p.lifecycleStatus = :status AND p.deletedAt IS NULL")
    fun findMemberIdsByLifecycleStatus(
        @Param("status") status: NewcomerLifecycle,
    ): List<Long>

    /** Lifecycle of the (non-deleted) newcomer profiles of many members, projected flat. */
    @Query(
        """
        SELECT new com.hanmaum.dn.app.features.newcomers.repository.NewcomerLifecycleView(
            p.member.id, p.lifecycleStatus
        )
        FROM NewcomerProfile p
        WHERE p.member.id IN :memberIds
          AND p.deletedAt IS NULL
        """,
    )
    fun findLifecycleByMemberIds(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<NewcomerLifecycleView>
}
