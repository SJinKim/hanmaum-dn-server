package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceLogDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceStatsDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.DefinitionDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.UpdateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.service.AttendanceService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
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

@RestController
@RequestMapping("/attendance")
class AttendanceController(
    private val attendanceService: AttendanceService,
) {
    // ─── Definitions ───────────────────────────────────────────────────────────

    @PostMapping("/definitions")
    @PreAuthorize("hasRole('ADMIN')")
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

    @PostMapping("/check-in")
    @PreAuthorize("isAuthenticated()")
    fun checkIn(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<AttendanceLogDto>> {
        val log = attendanceService.checkIn(jwt.subject)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = log, message = "출석 체크인 완료."))
    }

    // ─── Logs ─────────────────────────────────────────────────────────────────

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    fun getLogs(
        @RequestParam(required = false) memberId: UUID?,
        @RequestParam(required = false) definitionId: UUID?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate?,
    ): ResponseEntity<ApiResponse<List<AttendanceLogDto>>> {
        val effectiveFrom = from ?: LocalDate.now().minusDays(30)
        val effectiveTo = to ?: LocalDate.now()
        val logs = attendanceService.getLogs(memberId, definitionId, effectiveFrom, effectiveTo)
        return ResponseEntity.ok(ApiResponse.success(data = logs))
    }

    @GetMapping("/logs/me")
    @PreAuthorize("isAuthenticated()")
    fun getMyLogs(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApiResponse<List<AttendanceLogDto>>> {
        val logs = attendanceService.getMyLogs(jwt.subject)
        return ResponseEntity.ok(ApiResponse.success(data = logs))
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    fun getStats(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate?,
    ): ResponseEntity<ApiResponse<List<AttendanceStatsDto>>> {
        val effectiveFrom = from ?: LocalDate.now().minusDays(30)
        val effectiveTo = to ?: LocalDate.now()
        val stats = attendanceService.getStats(effectiveFrom, effectiveTo)
        return ResponseEntity.ok(ApiResponse.success(data = stats))
    }
}
