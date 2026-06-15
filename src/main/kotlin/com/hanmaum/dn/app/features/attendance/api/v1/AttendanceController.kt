package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceCheckInResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceGroupCountsResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.DefinitionDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.UpdateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.service.AttendanceService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

@RestController
@RequestMapping("/attendance")
class AttendanceController(
    private val attendanceService: AttendanceService,
) {
    // ─── Definitions ───────────────────────────────────────────────────────────

    @PostMapping("/definitions")
    @PreAuthorize("hasRole('ADMIN')")
    @OpenApiResponse(responseCode = "201", description = "Attendance definition created")
    fun createDefinition(
        @Valid @RequestBody request: CreateDefinitionRequest,
    ): ResponseEntity<ApiResponse<DefinitionDto>> {
        val created = attendanceService.createDefinition(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = created, message = "출석 정의가 생성되었습니다."))
    }

    @GetMapping("/definitions")
    @PreAuthorize("isAuthenticated()")
    fun getDefinitions(
        @RequestParam(defaultValue = "false") active: Boolean,
    ): ResponseEntity<ApiResponse<List<DefinitionDto>>> {
        val definitions = attendanceService.getDefinitions(active)
        return ResponseEntity.ok(ApiResponse.success(data = definitions))
    }

    @PatchMapping("/definitions/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateDefinition(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: UpdateDefinitionRequest,
    ): ResponseEntity<ApiResponse<DefinitionDto>> {
        val updated = attendanceService.updateDefinition(publicId, request)
        return ResponseEntity.ok(ApiResponse.success(data = updated))
    }

    @DeleteMapping("/definitions/{publicId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivateDefinition(
        @PathVariable publicId: UUID,
    ) {
        attendanceService.deactivateDefinition(publicId)
    }

    // ─── Check-in ─────────────────────────────────────────────────────────────

    /**
     * Records one attendance check-in for the authenticated member.
     *
     * The response intentionally contains no member identity or exact timestamp.
     */
    @PostMapping("/check-in")
    @PreAuthorize("isAuthenticated()")
    @OpenApiResponse(responseCode = "201", description = "Attendance check-in accepted")
    fun checkIn(authentication: JwtAuthenticationToken): ResponseEntity<ApiResponse<AttendanceCheckInResponse>> {
        val checkIn = attendanceService.checkIn(authentication.token.subject)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = checkIn, message = "출석 체크인 완료."))
    }

    /**
     * Returns attendance totals grouped by the member's church group at check-in.
     *
     * Active groups with no check-ins are included with a count of zero.
     */
    @GetMapping("/group-counts")
    @PreAuthorize("hasRole('ADMIN')")
    fun getGroupCounts(
        @RequestParam definitionId: UUID,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate,
    ): ResponseEntity<ApiResponse<AttendanceGroupCountsResponse>> {
        val counts = attendanceService.getGroupCounts(definitionId, date)
        return ResponseEntity.ok(ApiResponse.success(data = counts))
    }
}
