package com.hanmaum.dn.app.features.members.repository

import com.hanmaum.dn.app.features.members.domain.MemberClaimConflict
import org.springframework.data.jpa.repository.JpaRepository

interface MemberClaimConflictRepository : JpaRepository<MemberClaimConflict, Long> {
    fun existsByRegistrationMemberIdAndDeletedAtIsNull(registrationMemberId: Long): Boolean
}
