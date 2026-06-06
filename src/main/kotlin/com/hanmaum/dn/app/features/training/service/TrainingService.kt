package com.hanmaum.dn.app.features.training.service

import com.hanmaum.dn.app.features.training.api.toDto
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDto
import com.hanmaum.dn.app.features.training.repository.TrainingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TrainingService(
    private val trainingRepository: TrainingRepository,
) {
    /** The training catalog, ordered by progression (sort order). */
    @Transactional(readOnly = true)
    fun getTrainings(): List<TrainingDto> = trainingRepository.findAllByDeletedAtIsNullOrderBySortOrderAsc().map { it.toDto() }
}
