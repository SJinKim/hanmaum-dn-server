package com.hanmaum.dn.app.features.training.api

import com.hanmaum.dn.app.features.members.api.v1.dto.UserTrainingDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDetailDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDto
import com.hanmaum.dn.app.features.training.domain.Training
import com.hanmaum.dn.app.features.training.domain.UserTraining

fun Training.toDto(): TrainingDto =
    TrainingDto(
        publicId = this.publicId.toString(),
        name = this.name,
        sortOrder = this.sortOrder,
        description = this.description,
        startDate = this.startDate,
        durationWeeks = this.durationWeeks,
        openForRegistration = this.openForRegistration,
    )

/**
 * [registeredCount] is passed in rather than derived from the entity: it is a COUNT over
 * user_training that the service already had to run, and the entity has no association
 * back to its participants.
 */
fun Training.toDetailDto(registeredCount: Int): TrainingDetailDto =
    TrainingDetailDto(
        publicId = this.publicId.toString(),
        name = this.name,
        nameKo = this.nameKo,
        category = this.category?.name,
        sortOrder = this.sortOrder,
        description = this.description,
        startDate = this.startDate,
        durationWeeks = this.durationWeeks,
        openForRegistration = this.openForRegistration,
        weekday = this.weekday?.name,
        startTime = this.startTime,
        durationMinutes = this.durationMinutes,
        location = this.location,
        leaderName = this.leaderName,
        capacity = this.capacity,
        registeredCount = registeredCount,
        registrationDeadline = this.registrationDeadline,
        targetAudience = this.targetAudience.toList(),
    )

fun UserTraining.toDto(): UserTrainingDto =
    UserTrainingDto(
        trainingPublicId = this.training.publicId.toString(),
        name = this.training.name,
        status = this.status.name,
        completedAt = this.completedAt,
    )
