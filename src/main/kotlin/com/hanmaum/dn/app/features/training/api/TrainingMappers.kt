package com.hanmaum.dn.app.features.training.api

import com.hanmaum.dn.app.features.members.api.v1.dto.UserTrainingDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDto
import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.UserTraining

fun Training.toDto(): TrainingDto =
    TrainingDto(
        publicId = this.publicId.toString(),
        name = this.name,
        sortOrder = this.sortOrder,
    )

fun UserTraining.toDto(): UserTrainingDto =
    UserTrainingDto(
        trainingPublicId = this.training.publicId.toString(),
        name = this.training.name,
        status = this.status.name,
        completedAt = this.completedAt,
    )
