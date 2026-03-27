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
@RequestMapping("/api/v1/attendance")
class AttendanceController(
    private val service: AttendanceService,
) {
    // ─── Definition CRUD ───────────────────────────────────────────────────────

    @PostMapping("/definitions")
    @ResponseStatus(HttpStatus.CREATED)
    fun createDefinition(
        @Valid @RequestBody req: CreateDefinitionRequest,
    ): ApiResponse<DefinitionDto> =
        ApiResponse.success(service.createDefinition(req), "출석 정의가 생성되었습니다.")

    @GetMapping("/definitions")
    fun getDefinitions(
        @RequestParam(defaultValue = "false") activeOnly: Boolean,
    ): ApiResponse<List<DefinitionDto>> =
        ApiResponse.success(service.getDefinitions(activeOnly), "출석 정의 목록입니다.")

    @GetMapping("/definitions/{publicId}")
    fun getDefinition(
        @PathVariable publicId: UUID,
    ): ApiResponse<DefinitionDto> =
        ApiResponse.success(service.getDefinition(publicId), "출석 정의입니다.")

    @PatchMapping("/definitions/{publicId}")
    fun updateDefinition(
        @PathVariable publicId: UUID,
        @Valid @RequestBody req: UpdateDefinitionRequest,
    ): ApiResponse<DefinitionDto> =
        ApiResponse.success(service.updateDefinition(publicId, req), "출석 정의가 수정되었습니다.")

    @DeleteMapping("/definitions/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivateDefinition(
        @PathVariable publicId: UUID,
    ) {
        service.deactivateDefinition(publicId)
    }

    // ─── Check-in ─────────────────────────────────────────────────────────────

    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    fun checkIn(
        @AuthenticationPrincipal jwt: Jwt,
    ): ApiResponse<AttendanceLogDto> =
        ApiResponse.success(service.checkIn(jwt.subject), "출석 체크인 완료입니다.")

    // ─── Logs ─────────────────────────────────────────────────────────────────

    @GetMapping("/logs")
    fun getLogs(
        @RequestParam(required = false) memberPublicId: UUID?,
        @RequestParam(required = false) definitionPublicId: UUID?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ApiResponse<List<AttendanceLogDto>> =
        ApiResponse.success(service.getLogs(memberPublicId, definitionPublicId, from, to), "출석 로그 목록입니다.")

    @GetMapping("/my-logs")
    fun getMyLogs(
        @AuthenticationPrincipal jwt: Jwt,
    ): ApiResponse<List<AttendanceLogDto>> =
        ApiResponse.success(service.getMyLogs(jwt.subject), "내 출석 로그입니다.")

    // ─── Stats ────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    fun getStats(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ApiResponse<List<AttendanceStatsDto>> =
        ApiResponse.success(service.getStats(from, to), "출석 통계입니다.")
}
