package com.hanmaum.dn.app.features.ministry.repository

/** Flat projection of a member's active ministry assignment, for the members grid. */
data class MemberMinistryView(
    val memberId: Long,
    val ministryName: String,
)
