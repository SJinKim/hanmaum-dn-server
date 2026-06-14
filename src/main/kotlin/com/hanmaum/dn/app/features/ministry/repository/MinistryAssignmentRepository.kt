package com.hanmaum.dn.app.features.ministry.repository

import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
interface MinistryAssignmentRepository :
    JpaRepository<MinistryAssignment, Long>,
    MinistryAssignmentSecureQueries {
    /** A member's full ministry history across all ministries (for the detail view). */
    @Query(
        """
        SELECT a FROM MinistryAssignment a
        JOIN FETCH a.ministry
        WHERE a.member.id = :memberId
          AND a.deletedAt IS NULL
        ORDER BY a.startDate DESC, a.createdAt DESC
        """,
    )
    fun findByMemberId(
        @Param("memberId") memberId: Long,
    ): List<MinistryAssignment>

    /**
     * All active assignments (endDate IS NULL) for the given members, projected flat.
     */
    @Query(
        """
        SELECT new com.hanmaum.dn.app.features.ministry.repository.MemberMinistryView(
            a.member.id, a.ministry.name
        )
        FROM MinistryAssignment a
        WHERE a.member.id IN :memberIds
          AND a.deletedAt IS NULL
          AND a.endDate IS NULL
        """,
    )
    fun findActiveByMemberIds(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<MemberMinistryView>

    // NOTE: deliberately NOT clearAutomatically=true. Clearing the persistence context
    // here detaches the already-loaded `member` (and its lazy `group`), which made
    // MemberService.replaceMemberMinistries throw LazyInitializationException when it
    // built the response DTO. The service flush()es to order this delete before re-inserts.
    @Modifying
    @Transactional
    @Query("DELETE FROM MinistryAssignment a WHERE a.member.id = :memberId")
    fun deleteByMemberId(
        @Param("memberId") memberId: Long,
    )

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        """
        DELETE FROM MinistryAssignment a
        WHERE a.deleteEntryAt <= :now
          AND a.deletedAt IS NOT NULL
        """,
    )
    fun hardDeleteExpired(
        @Param("now") now: Instant,
    ): Int
}
