package com.hanmaum.dn.app.features.events.api.v1.dto

import com.fasterxml.jackson.annotation.JsonProperty
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
    @get:JsonProperty("isActive")
    val isActive: Boolean,
    val announcementPublicId: String?,
    val description: String? = null,
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
    /** When the next reminder for a MAYBE response is due, or null when none is pending. */
    val nextReminderAt: OffsetDateTime?,
)

/** Church-wide response backlog for currently open RSVPs. */
data class PendingEventRsvpSummaryDto(
    /** Distinct active members missing a response to at least one open RSVP. */
    val totalPending: Long,
    val rsvps: List<PendingEventRsvpDto>,
)

data class PendingEventRsvpDto(
    /** Public id of the open RSVP. */
    val publicId: String,
    val title: String,
    val windowEnd: OffsetDateTime,
    /** Active, non-deleted members eligible to respond. */
    val expected: Long,
    /** Eligible members with a non-deleted response. */
    val responded: Long,
    /** Eligible members who have not responded. */
    val pending: Long,
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
    @field:Size(max = 500, message = "설명은 최대 500자입니다.")
    val description: String? = null,
    /** Lets the 새 이벤트 추가 dialog save an event as 비활성 right away (바로 공개 off). */
    @get:JsonProperty("isActive")
    val isActive: Boolean = true,
)

/** PATCH semantics — only non-null fields applied. */
data class UpdateEventRsvpRequest(
    @field:Size(max = 100)
    val title: String? = null,
    val windowStart: OffsetDateTime? = null,
    val windowEnd: OffsetDateTime? = null,
    @get:JsonProperty("isActive")
    val isActive: Boolean? = null,
    val announcementId: UUID? = null,
    /** Blank clears the description; null leaves it unchanged. */
    @field:Size(max = 500, message = "설명은 최대 500자입니다.")
    val description: String? = null,
)
