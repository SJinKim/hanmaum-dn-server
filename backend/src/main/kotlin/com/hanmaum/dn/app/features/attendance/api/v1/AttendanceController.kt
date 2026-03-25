package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceLogDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CheckInRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CheckInStatusResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.service.AttendanceService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/attendance")
class AttendanceController(
    private val attendanceService: AttendanceService,
) {
    // 1. Status check
    @GetMapping("/status")
    fun getAttendanceStatus(
        @RequestParam memberId: String,
    ): CheckInStatusResponse = attendanceService.getCheckInStatus(memberId)

    // 2. Button click
    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    fun checkIn(
        @RequestBody checkInRequest: CheckInRequest,
    ) {
        attendanceService.checkIn(checkInRequest)
    }

    // 3. Historie
    @GetMapping("/history")
    fun getHistory(
        @RequestParam memberId: String,
    ): List<AttendanceLogDto> = attendanceService.getMyHistory(memberId)

    // 4. Admin: Definition erstellen
    @PostMapping("/definitions")
    fun createDefinition(
        @RequestBody req: CreateDefinitionRequest,
    ) {
        attendanceService.createDefinition(req)
    }
}
