package com.hanmaum.dn.app.features.groups.api.v1

import com.hanmaum.dn.app.features.groups.api.v1.dto.CreateMeetingRequest
import com.hanmaum.dn.app.features.groups.api.v1.dto.GroupMeetingDto
import com.hanmaum.dn.app.features.groups.api.v1.dto.MeetingDetailDto
import com.hanmaum.dn.app.features.groups.api.v1.dto.SubmitMeetingReportRequest
import com.hanmaum.dn.app.features.groups.service.GroupMeetingService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/group-meetings")
class GroupMeetingController(
    private val service: GroupMeetingService,
) {
    // ADMIN: Meeting erstellen
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createMeeting(
        @RequestBody req: CreateMeetingRequest,
    ) {
        service.createMeeting(req)
    }

    // LEADER: Bericht (Gebete) einreichen
    @PostMapping("/{id}/report")
    fun submitReport(
        @PathVariable id: Long,
        @RequestBody req: SubmitMeetingReportRequest,
    ) {
        service.submitReport(id, req)
    }

    // USER & ADMIN: Liste sehen
    @GetMapping
    fun getMeetings(
        @RequestParam myMemberId: String, // Public UUID
        @RequestParam(defaultValue = "false") isAdmin: Boolean, // Später echtes Role Checking!
    ): List<GroupMeetingDto> = service.getMeetings(myMemberId, isAdmin)

    // USER & ADMIN: Details (Gebete) sehen -> Mit Security Check im Service
    @GetMapping("/{id}")
    fun getMeetingDetails(
        @PathVariable id: Long,
        @RequestParam myMemberId: String,
        @RequestParam(defaultValue = "false") isAdmin: Boolean,
    ): MeetingDetailDto = service.getMeetingDetails(id, myMemberId, isAdmin)
}
