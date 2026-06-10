package com.hanmaum.dn.app.features.training.repository

import com.hanmaum.dn.app.features.training.domain.UserTraining
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserTrainingRepository : JpaRepository<UserTraining, Long> {
    /** A member's full training history (for the detail view). */
    @Query(
        """
        SELECT ut FROM UserTraining ut
        JOIN FETCH ut.training t
        WHERE ut.member.id = :memberId
          AND ut.deletedAt IS NULL
        ORDER BY t.sortOrder ASC
        """,
    )
    fun findByMemberId(
        @Param("memberId") memberId: Long,
    ): List<UserTraining>

    /**
     * All trainings (any status) for the given members, projected flat with status.
     * The service groups these by member and orders by sortOrder to build the grid chips.
     */
    @Query(
        """
        SELECT new com.hanmaum.dn.app.features.training.repository.MemberTrainingStatusView(
            ut.member.id, t.name, ut.status, t.sortOrder
        )
        FROM UserTraining ut
        JOIN ut.training t
        WHERE ut.member.id IN :memberIds
          AND ut.deletedAt IS NULL
        """,
    )
    fun findByMemberIds(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<MemberTrainingStatusView>

    /** Replace-set support: removes a member's existing rows before re-insert. */
    fun deleteByMemberId(memberId: Long)
}
