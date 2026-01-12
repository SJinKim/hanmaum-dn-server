package com.hanmaum.dn.app.features.attendance.api

import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceLogDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CheckInRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CheckInStatusResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.service.AttendanceService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/attendance")
class AttendanceController(private val attendanceService: AttendanceService) {

    // 1. Status check
    @GetMapping("/status")
    fun getAttendanceStatus(@RequestParam memberId: String): CheckInStatusResponse {
        return attendanceService.getCheckInStatus(memberId)
    }

    // 2. Button click
    @PostMapping("/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    fun checkIn(@RequestBody checkInRequest: CheckInRequest) {
        attendanceService.checkIn(checkInRequest)
    }

    // 3. Historie
    @GetMapping("/history")
    fun getHistory(@RequestParam memberId: String): List<AttendanceLogDto> {
        return attendanceService.getMyHistory(memberId)
    }

    // 4. Admin: Definition erstellen
    @PostMapping("/definitions")
fun createDefinition(@RequestBody req: CreateDefinitionRequest) {
    attendanceService.createDefinition(req)
}

}