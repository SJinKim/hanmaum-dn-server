package com.hanmaum.dn.app.features.events.api.v1.dto

import com.hanmaum.dn.app.features.events.domain.RsvpStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

data class EventRsvpDto(
    val publicId: String,
    val title: String,
    val windowStart: OffsetDateTime,
    val windowEnd: OffsetDateTime,
    val isActive: Boolean,
    val announcementPublicId: String?,
)

data class ActiveEventRsvpDto(
    val publicId: String,
    val title: String,
    val windowStart: OffsetDateTime,
    val windowEnd: OffsetDateTime,
    /** Public id of the linked EVENT announcement, or null when the RSVP is standalone. */
    val announcementId: UUID?,
    /** The authenticated member's response, or null when they have not responded. */
    val myStatus: RsvpStatus?,
    /** Time of the authenticated member's latest status change. */
    val respondedAt: OffsetDateTime?,
)

data class EventRsvpResponseRequest(
    /** The authenticated member's RSVP response. */
    val status: RsvpStatus,
)

data class EventRsvpResponseDto(
    /** Public id of the event RSVP. */
    val eventPublicId: String,
    /** Display title of the event RSVP. */
    val eventTitle: String,
    /** The authenticated member's current RSVP response. */
    val status: RsvpStatus,
    /** Time when this status was first set or last changed. */
    val respondedAt: OffsetDateTime,
)

data class EventCheckInResponse(
    val eventPublicId: String,
    val eventTitle: String,
    val checkedInAt: OffsetDateTime,
)

data class EventAttendeeDto(
    val memberName: String,
    val groupName: String?,
    val groupDivision: String?,
    val checkedInAt: OffsetDateTime,
    /** The member's current RSVP response. */
    val status: RsvpStatus,
)

data class EventAttendeesResponse(
    val eventPublicId: String,
    val eventTitle: String,
    val totalCount: Int,
    /** Number of members who answered GOING. */
    val goingCount: Int,
    /** Number of members who answered NOT_GOING. */
    val notGoingCount: Int,
    /** Number of members who answered MAYBE. */
    val maybeCount: Int,
    val attendees: List<EventAttendeeDto>,
)

data class CreateEventRsvpRequest(
    @field:NotBlank(message = "제목은 필수입니다.")
    @field:Size(max = 100, message = "제목은 최대 100자입니다.")
    val title: String,
    @field:NotNull(message = "시작 시간은 필수입니다.")
    val windowStart: OffsetDateTime,
    @field:NotNull(message = "종료 시간은 필수입니다.")
    val windowEnd: OffsetDateTime,
    val announcementId: UUID? = null,
)

/** PATCH semantics — only non-null fields applied. */
data class UpdateEventRsvpRequest(
    @field:Size(max = 100)
    val title: String? = null,
    val windowStart: OffsetDateTime? = null,
    val windowEnd: OffsetDateTime? = null,
    val isActive: Boolean? = null,
    val announcementId: UUID? = null,
)
