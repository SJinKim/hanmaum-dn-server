package com.hanmaum.dn.app.features.training.repository

import com.hanmaum.dn.app.features.training.domain.TrainingStatus

/**
 * Flat projection of a member's training including [status], used to render the
 * full set of training chips (grey = COMPLETED, orange = IN_PROGRESS) in the
 * members grid. [sortOrder] drives the chip ordering (training progression).
 */
data class MemberTrainingStatusView(
    val memberId: Long,
    val trainingName: String,
    val status: TrainingStatus,
    val sortOrder: Int,
)
