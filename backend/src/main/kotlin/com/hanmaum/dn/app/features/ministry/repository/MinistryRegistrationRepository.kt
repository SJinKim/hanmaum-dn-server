package com.hanmaum.dn.app.features.ministry.repository

import com.hanmaum.dn.app.features.ministry.domain.MinistryRegistration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
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
     * Check for duplicate: same ministry + member + period.
     * Uses `member_Id` (underscore) because `member` is a @ManyToOne association;
     * Spring Data traverses member.id to generate the predicate.
     */
    fun existsByMinistryIdAndMemberIdAndRegistrationPeriodAndDeletedAtIsNull(
        ministryId: Long,
        memberId: Long,
        registrationPeriod: String,
    ): Boolean
}
