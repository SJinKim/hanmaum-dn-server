package com.hanmaum.dn.app.features.events.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.events.api.v1.dto.ActiveEventRsvpDto
import com.hanmaum.dn.app.features.events.api.v1.dto.CreateEventRsvpRequest
import com.hanmaum.dn.app.features.events.api.v1.dto.EventAttendeesResponse
import com.hanmaum.dn.app.features.events.api.v1.dto.EventCheckInResponse
import com.hanmaum.dn.app.features.events.api.v1.dto.EventRsvpDto
import com.hanmaum.dn.app.features.events.api.v1.dto.EventRsvpResponseDto
import com.hanmaum.dn.app.features.events.api.v1.dto.EventRsvpResponseRequest
import com.hanmaum.dn.app.features.events.api.v1.dto.UpdateEventRsvpRequest
import com.hanmaum.dn.app.features.events.service.EventRsvpService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/events/rsvps")
class EventRsvpController(
    private val eventRsvpService: EventRsvpService,
) {
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GROUP_LEADER')")
    fun createRsvp(
        @Valid @RequestBody request: CreateEventRsvpRequest,
    ): ResponseEntity<ApiResponse<EventRsvpDto>> {
        val created = eventRsvpService.createRsvp(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = created, message = "이벤트 RSVP가 생성되었습니다."))
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun listRsvps(): ResponseEntity<ApiResponse<List<EventRsvpDto>>> =
        ResponseEntity.ok(ApiResponse.success(data = eventRsvpService.listAllRsvps()))

    /** Lists currently open event RSVPs together with the authenticated member's response. */
    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    fun getActiveRsvps(authentication: JwtAuthenticationToken): ResponseEntity<ApiResponse<List<ActiveEventRsvpDto>>> =
        ResponseEntity.ok(ApiResponse.success(data = eventRsvpService.getActiveRsvps(authentication.token.subject)))

    @PatchMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GROUP_LEADER')")
    fun updateRsvp(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: UpdateEventRsvpRequest,
    ): ResponseEntity<ApiResponse<EventRsvpDto>> {
        val updated = eventRsvpService.updateRsvp(publicId, request)
        return ResponseEntity.ok(ApiResponse.success(data = updated))
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GROUP_LEADER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivateRsvp(
        @PathVariable publicId: UUID,
    ) {
        eventRsvpService.deactivateRsvp(publicId)
    }

    /** Backward-compatible shortcut that sets the authenticated member's response to GOING. */
    @PostMapping("/{publicId}/check-in")
    @PreAuthorize("isAuthenticated()")
    fun checkIn(
        @PathVariable publicId: UUID,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<EventCheckInResponse>> {
        val response = eventRsvpService.checkIn(publicId, authentication.token.subject)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(data = response, message = "RSVP 신청 완료."))
    }

    /** Sets or changes the authenticated member's response for an active event RSVP. */
    @PutMapping("/{publicId}/response")
    @PreAuthorize("isAuthenticated()")
    fun setResponse(
        @PathVariable publicId: UUID,
        @Valid @RequestBody request: EventRsvpResponseRequest,
        authentication: JwtAuthenticationToken,
    ): ResponseEntity<ApiResponse<EventRsvpResponseDto>> =
        ResponseEntity.ok(
            ApiResponse.success(
                data = eventRsvpService.setResponse(publicId, authentication.token.subject, request.status),
            ),
        )

    @GetMapping("/{publicId}/attendees")
    @PreAuthorize("hasAnyRole('ADMIN', 'GROUP_LEADER')")
    fun getAttendees(
        @PathVariable publicId: UUID,
    ): ResponseEntity<ApiResponse<EventAttendeesResponse>> =
        ResponseEntity.ok(ApiResponse.success(data = eventRsvpService.getAttendees(publicId)))
}
