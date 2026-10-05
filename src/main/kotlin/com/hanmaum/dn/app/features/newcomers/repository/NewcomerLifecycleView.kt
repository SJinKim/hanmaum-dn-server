package com.hanmaum.dn.app.features.newcomers.repository

import com.hanmaum.dn.app.features.newcomers.domain.NewcomerLifecycle

/** Flat projection of a newcomer profile's lifecycle, carrying no PII. Batched into the members grid. */
data class NewcomerLifecycleView(
    val memberId: Long,
    val lifecycleStatus: NewcomerLifecycle,
)
