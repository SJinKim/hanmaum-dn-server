package com.hanmaum.dn.app.features.ministry.repository

import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignmentStatus
import jakarta.persistence.EntityManager
import java.util.UUID

interface MinistryAssignmentSecureQueries {
    fun findActiveByMinistryPublicId(ministryPublicId: UUID): List<ActiveMemberView>

    fun findByMinistryPublicIdIncludingEnded(ministryPublicId: UUID): List<ActiveMemberView>

    fun findPendingByMinistryPublicId(ministryPublicId: UUID): List<ActiveMemberView>
}

class MinistryAssignmentRepositoryImpl(
    private val entityManager: EntityManager,
) : MinistryAssignmentSecureQueries {
    override fun findActiveByMinistryPublicId(ministryPublicId: UUID): List<ActiveMemberView> =
        findVisibleMembers(ministryPublicId, includeEnded = false, status = MinistryAssignmentStatus.ACTIVE)

    override fun findByMinistryPublicIdIncludingEnded(ministryPublicId: UUID): List<ActiveMemberView> =
        findVisibleMembers(ministryPublicId, includeEnded = true, status = MinistryAssignmentStatus.ACTIVE)

    override fun findPendingByMinistryPublicId(ministryPublicId: UUID): List<ActiveMemberView> =
        findVisibleMembers(ministryPublicId, includeEnded = false, status = MinistryAssignmentStatus.PENDING)

    private fun findVisibleMembers(
        ministryPublicId: UUID,
        includeEnded: Boolean,
        status: MinistryAssignmentStatus,
    ): List<ActiveMemberView> =
        entityManager
            .createQuery(
                """
                SELECT a FROM MinistryAssignment a
                JOIN FETCH a.member
                WHERE a.ministry.publicId = :ministryPublicId
                  AND (:includeEnded = true OR a.endDate IS NULL)
                  AND (a.status = :status OR (:includeEnded = true AND a.endDate IS NOT NULL AND a.selfIntroduction IS NULL))
                  AND a.deletedAt IS NULL
                  AND a.member.deletedAt IS NULL
                ORDER BY a.startDate ASC, a.createdAt ASC
                """.trimIndent(),
                com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment::class.java,
            ).setParameter("ministryPublicId", ministryPublicId)
            .setParameter("includeEnded", includeEnded)
            .setParameter("status", status)
            .resultList
            .filter { status != MinistryAssignmentStatus.PENDING || it.selfIntroduction != null }
            .map { assignment ->
                ActiveMemberView(
                    memberPublicId = assignment.member.publicId,
                    fullName = assignment.member.getFullName(),
                    startDate = assignment.startDate,
                    note = assignment.note,
                    gender = assignment.member.gender,
                    role = assignment.role,
                    status = assignment.status,
                    endDate = assignment.endDate,
                    selfIntroduction = if (status == MinistryAssignmentStatus.PENDING) assignment.selfIntroduction else null,
                    appliedAt = if (status == MinistryAssignmentStatus.PENDING) assignment.createdAt else null,
                )
            }
}
