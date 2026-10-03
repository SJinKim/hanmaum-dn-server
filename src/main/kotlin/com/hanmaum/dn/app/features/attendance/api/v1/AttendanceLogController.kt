package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceLogResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateAttendanceLogRequest
import com.hanmaum.dn.app.features.attendance.service.AttendanceRosterService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID
import io.swagger.v3.oas.annotations.responses.ApiResponse as OpenApiResponse

/** The admin 체크인 명단, person by person (#224). ADMIN only. */
@RestController
@RequestMapping("/attendance/logs")
@PreAuthorize("hasRole('ADMIN')")
class AttendanceLogController(
    private val rosterService: AttendanceRosterService,
) {
    @GetMapping
    fun getLogs(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @RequestParam(required = false) definitionId: UUID?,
    ): ResponseEntity<ApiResponse<List<AttendanceLogResponse>>> =
        ResponseEntity.ok(ApiResponse.success(data = rosterService.getRoster(date, definitionId)))

    @PostMapping
    @OpenApiResponse(responseCode = "201", description = "Attendance log added")
    @OpenApiResponse(responseCode = "409", description = "The member already has a log for this definition and date")
    fun addLog(
        @Valid @RequestBody request: CreateAttendanceLogRequest,
    ): ResponseEntity<ApiResponse<AttendanceLogResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = rosterService.addLog(request), message = "출석이 추가되었습니다."))
}
