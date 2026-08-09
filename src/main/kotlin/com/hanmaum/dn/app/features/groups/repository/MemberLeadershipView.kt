package com.hanmaum.dn.app.features.groups.repository

import java.time.LocalDate

/**
 * Flat projection of a member's *current* group-leader tenure, for the members grid and detail.
 * Carries no PII — only the internal member id and the tenure start — so it is safe to select
 * directly instead of loading whole [com.hanmaum.dn.app.features.groups.domain.GroupLeader] rows.
 */
data class MemberLeadershipView(
    val memberId: Long,
    val startDate: LocalDate,
)
