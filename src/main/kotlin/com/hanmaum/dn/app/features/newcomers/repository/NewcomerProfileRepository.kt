package com.hanmaum.dn.app.features.newcomers.repository

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
}
