package com.hanmaum.dn.app.features.training.repository

import com.hanmaum.dn.app.features.training.domain.TrainingStatus
import com.hanmaum.dn.app.features.training.domain.TrainingVariant
import com.hanmaum.dn.app.features.training.domain.UserTraining
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

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

    /**
     * Looks up one participation the way uq_user_training_member_training_variant keys
     * it. `variant IS NULL` has to be spelled out because SQL equality never matches
     * NULL, which is the same reason the index uses COALESCE(variant, '').
     */
    @Query(
        """
        SELECT ut FROM UserTraining ut
        WHERE ut.member.id = :memberId
          AND ut.training.id = :trainingId
          AND (:variant IS NULL AND ut.variant IS NULL OR ut.variant = :variant)
          AND ut.deletedAt IS NULL
        """,
    )
    fun findByMemberIdAndTrainingIdAndVariantAndDeletedAtIsNull(
        @Param("memberId") memberId: Long,
        @Param("trainingId") trainingId: Long,
        @Param("variant") variant: TrainingVariant?,
    ): Optional<UserTraining>

    /**
     * How many members hold one of [statuses] for a course.
     *
     * The caller decides which statuses count as "signed up for the run that is open
     * now" — see TrainingService.REGISTERED_STATUSES. Passing them in keeps that
     * business rule out of the repository, where a change to it would be invisible.
     */
    fun countByTrainingIdAndStatusInAndDeletedAtIsNull(
        trainingId: Long,
        statuses: Collection<TrainingStatus>,
    ): Int

    /** Replace-set support: removes a member's existing rows before re-insert. */
    fun deleteByMemberId(memberId: Long)
}
