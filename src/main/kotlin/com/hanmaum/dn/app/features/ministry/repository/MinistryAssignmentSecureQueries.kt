package com.hanmaum.dn.app.features.ministry.repository

import jakarta.persistence.EntityManager
import java.util.UUID

interface MinistryAssignmentSecureQueries {
    fun findActiveByMinistryPublicId(ministryPublicId: UUID): List<ActiveMemberView>
}

class MinistryAssignmentRepositoryImpl(
    private val entityManager: EntityManager,
) : MinistryAssignmentSecureQueries {
    override fun findActiveByMinistryPublicId(ministryPublicId: UUID): List<ActiveMemberView> =
        entityManager
            .createQuery(
                """
                SELECT a FROM MinistryAssignment a
                JOIN FETCH a.member
                WHERE a.ministry.publicId = :ministryPublicId
                  AND a.endDate IS NULL
                  AND a.deletedAt IS NULL
                  AND a.member.deletedAt IS NULL
                ORDER BY a.startDate ASC
                """.trimIndent(),
                com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment::class.java,
            ).setParameter("ministryPublicId", ministryPublicId)
            .resultList
            .map { assignment ->
                ActiveMemberView(
                    memberPublicId = assignment.member.publicId,
                    fullName = assignment.member.getFullName(),
                    startDate = assignment.startDate,
                    note = assignment.note,
                    gender = assignment.member.gender,
                )
            }
}
