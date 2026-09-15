package com.hanmaum.dn.app.features.courseapplication.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.courseapplication.service.CourseApplicationService
import com.hanmaum.dn.app.features.training.api.v1.dto.MyTrainingApplicationDto
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The caller's own 양육 applications. The member is resolved from the JWT subject; no
 * member id is accepted, so no member can read another's applications here.
 */
@RestController
@RequestMapping("/me")
class MyTrainingApplicationController(
    private val courseApplicationService: CourseApplicationService,
) {
    /**
     * GET /api/v1/me/trainings
     * Role: MEMBER — the 양육 section of 나의 신청: every application made through the app,
     * newest first, with when it was made and where it stands.
     *
     * Read from this server alone, so it answers even while application.hanmaum.de is down.
     */
    @GetMapping("/trainings")
    @PreAuthorize("isAuthenticated()")
    fun getMyTrainingApplications(authentication: JwtAuthenticationToken): ResponseEntity<ApiResponse<List<MyTrainingApplicationDto>>> =
        ResponseEntity.ok(
            ApiResponse.success(data = courseApplicationService.myTrainingApplications(authentication.token.subject)),
        )
}
