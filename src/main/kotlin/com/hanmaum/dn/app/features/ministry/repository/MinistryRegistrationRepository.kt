package com.hanmaum.dn.app.features.ministry.repository

import com.hanmaum.dn.app.features.ministry.domain.MinistryRegistration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface MinistryRegistrationRepository : JpaRepository<MinistryRegistration, Long> {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<MinistryRegistration>

    /**
     * List all non-deleted registrations for a ministry.
     * [period] optional; filters by year string (e.g. "2026").
     */
    @Query(
        """
        SELECT r FROM MinistryRegistration r
        WHERE r.ministry.id = :ministryId
          AND r.deletedAt IS NULL
          AND (:period IS NULL OR r.registrationPeriod = :period)
        ORDER BY r.createdAt ASC
        """,
    )
    fun findByMinistryId(
        @Param("ministryId") ministryId: Long,
        @Param("period") period: String?,
    ): List<MinistryRegistration>

    /**
     * Check for duplicate: same ministry + member + period + not deleted.
     * Used to detect PENDING or APPROVED records before a new registration.
     */
    fun existsByMinistryIdAndMemberIdAndRegistrationPeriodAndDeletedAtIsNull(
        ministryId: Long,
        memberId: Long,
        registrationPeriod: String,
    ): Boolean

    /**
     * Find a member's own registration for a specific ministry + period.
     * Returns non-deleted records regardless of status.
     */
    @Query(
        """
        SELECT r FROM MinistryRegistration r
        WHERE r.ministry.id = :ministryId
          AND r.member.id = :memberId
          AND r.registrationPeriod = :period
          AND r.deletedAt IS NULL
        """,
    )
    fun findByMinistryIdAndMemberIdAndPeriod(
        @Param("ministryId") ministryId: Long,
        @Param("memberId") memberId: Long,
        @Param("period") period: String,
    ): Optional<MinistryRegistration>

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        """
        DELETE FROM MinistryRegistration r
        WHERE r.deleteEntryAt <= :now
          AND r.deletedAt IS NOT NULL
        """,
    )
    fun hardDeleteExpired(
        @Param("now") now: Instant,
    ): Int
}
