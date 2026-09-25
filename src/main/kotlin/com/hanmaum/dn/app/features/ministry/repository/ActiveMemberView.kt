package com.hanmaum.dn.app.features.ministry.repository

import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignmentRole
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignmentStatus
import java.time.LocalDate
import java.util.UUID

/** Flat projection of one member's active ministry assignment, for the ministry detail view. */
data class ActiveMemberView(
    val memberPublicId: UUID,
    val fullName: String,
    val startDate: LocalDate,
    val note: String?,
    val gender: Gender?,
    val role: MinistryAssignmentRole = MinistryAssignmentRole.MEMBER,
    val status: MinistryAssignmentStatus = MinistryAssignmentStatus.ACTIVE,
    val endDate: LocalDate? = null,
)
