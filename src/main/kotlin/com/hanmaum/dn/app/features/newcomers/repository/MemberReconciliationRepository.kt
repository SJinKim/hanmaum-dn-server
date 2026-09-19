package com.hanmaum.dn.app.features.newcomers.repository

import com.hanmaum.dn.app.features.newcomers.domain.MemberReconciliation
import com.hanmaum.dn.app.features.newcomers.domain.ReconciliationStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MemberReconciliationRepository : JpaRepository<MemberReconciliation, Long> {
    fun findAllByRegistrationMemberIdAndDeletedAtIsNull(registrationMemberId: Long): List<MemberReconciliation>

    fun findByRegistrationMemberIdAndStatusAndDeletedAtIsNull(
        registrationMemberId: Long,
        status: ReconciliationStatus,
    ): MemberReconciliation?

    @EntityGraph(attributePaths = ["registrationMember", "selectedMember", "candidateMemberIds"])
    fun findAllByStatusAndDeletedAtIsNull(
        status: ReconciliationStatus,
        pageable: Pageable,
    ): Page<MemberReconciliation>

    @EntityGraph(attributePaths = ["registrationMember", "selectedMember", "candidateMemberIds"])
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): MemberReconciliation?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = ["registrationMember", "selectedMember", "candidateMemberIds"])
    @Query("SELECT r FROM MemberReconciliation r WHERE r.publicId = :publicId AND r.deletedAt IS NULL")
    fun findForUpdate(
        @Param("publicId") publicId: UUID,
    ): MemberReconciliation?
}
