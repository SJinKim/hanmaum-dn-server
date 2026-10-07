package com.hanmaum.dn.app.features.groups.api.v1

import com.hanmaum.dn.app.common.security.Roles
import com.hanmaum.dn.app.features.groups.api.v1.dto.CreateMeetingRequest
import com.hanmaum.dn.app.features.groups.api.v1.dto.GroupMeetingDto
import com.hanmaum.dn.app.features.groups.api.v1.dto.MeetingDetailDto
import com.hanmaum.dn.app.features.groups.api.v1.dto.SubmitMeetingReportRequest
import com.hanmaum.dn.app.features.groups.service.GroupMeetingService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/group-meetings")
class GroupMeetingController(
    private val service: GroupMeetingService,
) {
    // ADMIN: Meeting erstellen
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    fun createMeeting(
        @RequestBody req: CreateMeetingRequest,
    ) {
        service.createMeeting(req)
    }

    // ADMIN oder aktiver 순장 der Gruppe: Bericht (Gebete) einreichen
    @PostMapping("/{publicId}/report")
    @PreAuthorize("hasAnyRole('ADMIN', 'GROUP_LEADER')")
    fun submitReport(
        @PathVariable publicId: UUID,
        @RequestBody req: SubmitMeetingReportRequest,
        authentication: JwtAuthenticationToken,
    ) {
        service.submitReport(publicId, req, authentication.token.subject, authentication.isAdmin())
    }

    // USER: eigene Gruppe, ADMIN: alle
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun getMeetings(authentication: JwtAuthenticationToken): List<GroupMeetingDto> =
        service.getMeetings(authentication.token.subject, authentication.isAdmin())

    // USER: nur eigene Gruppe (Gebete), ADMIN: alle
    @GetMapping("/{publicId}")
    @PreAuthorize("isAuthenticated()")
    fun getMeetingDetails(
        @PathVariable publicId: UUID,
        authentication: JwtAuthenticationToken,
    ): MeetingDetailDto = service.getMeetingDetails(publicId, authentication.token.subject, authentication.isAdmin())

    private fun JwtAuthenticationToken.isAdmin(): Boolean = authorities.any { it.authority == Roles.authority(Roles.ADMIN) }
}
