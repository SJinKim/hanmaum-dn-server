package com.hanmaum.dn.app.features.ministry.repository

import java.time.LocalDate
import java.util.UUID

/** Flat projection of one member's active ministry assignment, for the ministry detail view. */
data class ActiveMemberView(
    val memberPublicId: UUID,
    val fullName: String,
    val startDate: LocalDate,
    val note: String?,
)
