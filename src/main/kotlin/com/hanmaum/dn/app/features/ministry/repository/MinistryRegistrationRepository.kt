package com.hanmaum.dn.app.features.ministry.repository

import com.hanmaum.dn.app.features.ministry.domain.MinistryRegistration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
interface MinistryRegistrationRepository : JpaRepository<MinistryRegistration, Long> {
    /** A member's full ministry history across all ministries (for the detail view). */
    @Query(
        """
        SELECT r FROM MinistryRegistration r
        JOIN FETCH r.ministry
        WHERE r.member.id = :memberId
          AND r.deletedAt IS NULL
        ORDER BY r.registrationPeriod DESC, r.createdAt DESC
        """,
    )
    fun findByMemberId(
        @Param("memberId") memberId: Long,
    ): List<MinistryRegistration>

    /**
     * All APPROVED registrations for the given members, projected flat.
     * The service reduces these to the most recent registrationPeriod per member
     * to derive the "active ministry" shown in the grid.
     */
    @Query(
        """
        SELECT new com.hanmaum.dn.app.features.ministry.repository.MemberMinistryView(
            r.member.id, r.ministry.name, r.registrationPeriod
        )
        FROM MinistryRegistration r
        WHERE r.member.id IN :memberIds
          AND r.deletedAt IS NULL
          AND r.status = com.hanmaum.dn.app.features.ministry.domain.RegistrationStatus.APPROVED
        """,
    )
    fun findApprovedByMemberIds(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<MemberMinistryView>

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
