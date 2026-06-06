package com.hanmaum.dn.app.features.training.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingDto
import com.hanmaum.dn.app.features.training.service.TrainingService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/trainings")
class TrainingController(
    private val trainingService: TrainingService,
) {
    /**
     * GET /api/v1/trainings
     * Role: ADMIN — the training catalog used to populate the member edit form.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun listTrainings(): ResponseEntity<ApiResponse<List<TrainingDto>>> {
        val trainings = trainingService.getTrainings()
        return ResponseEntity.ok(ApiResponse.success(data = trainings))
    }
}
