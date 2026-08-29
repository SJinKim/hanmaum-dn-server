package com.hanmaum.dn.app.features.members.repository

import com.hanmaum.dn.app.features.members.domain.MemberGraduation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface MemberGraduationRepository : JpaRepository<MemberGraduation, Long> {
    /**
     * The member's open graduation, or null. Returns a single row safely: the partial
     * unique index uq_member_graduations_open guarantees there is at most one.
     */
    @Query(
        """
        SELECT g FROM MemberGraduation g
        WHERE g.member.id = :memberId
          AND g.revertedAt IS NULL
          AND g.deletedAt IS NULL
        """,
    )
    fun findOpenByMemberId(
        @Param("memberId") memberId: Long,
    ): MemberGraduation?

    /** Full history for one member, most recent first. Includes reverted graduations. */
    @Query(
        """
        SELECT g FROM MemberGraduation g
        WHERE g.member.id = :memberId
          AND g.deletedAt IS NULL
        ORDER BY g.graduatedOn DESC, g.id DESC
        """,
    )
    fun findAllByMemberIdOrderByGraduatedOnDesc(
        @Param("memberId") memberId: Long,
    ): List<MemberGraduation>

    /** Open graduations for many members, projected flat. Backs the members grid. */
    @Query(
        """
        SELECT new com.hanmaum.dn.app.features.members.repository.MemberGraduationView(
            g.member.id, g.graduatedOn
        )
        FROM MemberGraduation g
        WHERE g.member.id IN :memberIds
          AND g.revertedAt IS NULL
          AND g.deletedAt IS NULL
        """,
    )
    fun findOpenByMemberIds(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<MemberGraduationView>
}
