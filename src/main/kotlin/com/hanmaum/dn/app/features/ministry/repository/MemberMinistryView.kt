package com.hanmaum.dn.app.features.ministry.repository

/**
 * Flat projection of a member's APPROVED ministry registration, used to compute
 * the "active ministry" (most recent [registrationPeriod]) for the members grid.
 */
data class MemberMinistryView(
    val memberId: Long,
    val ministryName: String,
    val registrationPeriod: String,
)
