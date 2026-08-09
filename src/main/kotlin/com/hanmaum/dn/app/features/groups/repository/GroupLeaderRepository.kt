package com.hanmaum.dn.app.features.groups.repository

import com.hanmaum.dn.app.features.groups.domain.GroupLeader
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface GroupLeaderRepository : JpaRepository<GroupLeader, Long> {
    /** The group's current leader tenure, or null when the group has never had one / is vacant. */
    @Query(
        """
        SELECT l FROM GroupLeader l
        WHERE l.group.id = :groupId
          AND l.endDate IS NULL
          AND l.deletedAt IS NULL
        """,
    )
    fun findActiveByGroupId(
        @Param("groupId") groupId: Long,
    ): GroupLeader?

    /**
     * Current leader tenures for the given members, projected flat (no PII).
     * Batched so the members grid stays free of N+1 queries.
     */
    @Query(
        """
        SELECT new com.hanmaum.dn.app.features.groups.repository.MemberLeadershipView(
            l.member.id, l.startDate
        )
        FROM GroupLeader l
        WHERE l.member.id IN :memberIds
          AND l.endDate IS NULL
          AND l.deletedAt IS NULL
        """,
    )
    fun findActiveByMemberIds(
        @Param("memberIds") memberIds: Collection<Long>,
    ): List<MemberLeadershipView>

    /**
     * Current leader tenures for the given groups, with the member eagerly fetched.
     *
     * Returns entities rather than a flat projection on purpose: the leader's name lives in
     * `members.last_name` / `first_name`, which are encrypted at rest. Reading them off a loaded
     * entity runs the PII converters; hand-rolling the name into a JPQL constructor projection
     * would not.
     */
    @Query(
        """
        SELECT l FROM GroupLeader l
        JOIN FETCH l.member
        WHERE l.group.id IN :groupIds
          AND l.endDate IS NULL
          AND l.deletedAt IS NULL
        """,
    )
    fun findActiveByGroupIds(
        @Param("groupIds") groupIds: Collection<Long>,
    ): List<GroupLeader>
}
