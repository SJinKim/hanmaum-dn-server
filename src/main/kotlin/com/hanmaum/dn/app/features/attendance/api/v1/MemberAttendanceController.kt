package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.MemberAttendanceHistoryResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.MemberAttendanceSummaryResponse
import com.hanmaum.dn.app.features.attendance.service.MemberAttendanceService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * The caller's own attendance. Every endpoint resolves the member from the JWT subject;
 * none of them accepts a member id, so no member can read another's history here.
 */
@RestController
@RequestMapping("/me")
class MemberAttendanceController(
    private val memberAttendanceService: MemberAttendanceService,
) {
    /**
     * GET /api/v1/me/attendance?from=2026-06-01&to=2026-08-30
     * Role: MEMBER — the 최근 출석 list, one entry per scheduled occurrence marked
     * 출석 or 미출석, newest first.
     *
     * Both parameters are optional: `to` defaults to today and is capped at it, `from`
     * defaults to 90 days before `to`. The response echoes the range actually used.
     * 400 when `from` is after `to` or the range exceeds a year.
     */
    @GetMapping("/attendance")
    @PreAuthorize("isAuthenticated()")
    fun getMyAttendance(
        authentication: JwtAuthenticationToken,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate?,
    ): ResponseEntity<ApiResponse<MemberAttendanceHistoryResponse>> =
        ResponseEntity.ok(
            ApiResponse.success(data = memberAttendanceService.getHistory(authentication.token.subject, from, to)),
        )

    /**
     * GET /api/v1/me/attendance/summary
     * Role: MEMBER — the counters behind the 이번 달 출석 tile and the 올해 출석 figure.
     */
    @GetMapping("/attendance/summary")
    @PreAuthorize("isAuthenticated()")
    fun getMyAttendanceSummary(authentication: JwtAuthenticationToken): ResponseEntity<ApiResponse<MemberAttendanceSummaryResponse>> =
        ResponseEntity.ok(
            ApiResponse.success(data = memberAttendanceService.getSummary(authentication.token.subject)),
        )
}
