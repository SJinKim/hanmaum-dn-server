package com.hanmaum.dn.app.features.members.repository

import java.time.LocalDate

/**
 * Flat projection of an open graduation, carrying no PII. Batched into the members grid
 * the same way MemberLeadershipView is, so the listing stays free of N+1 queries.
 */
data class MemberGraduationView(
    val memberId: Long,
    val graduatedOn: LocalDate,
)
