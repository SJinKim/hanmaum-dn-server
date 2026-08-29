package com.hanmaum.dn.app.features.events.api

import com.hanmaum.dn.app.features.events.api.v1.dto.ActiveEventRsvpDto
import com.hanmaum.dn.app.features.events.api.v1.dto.EventAttendeeDto
import com.hanmaum.dn.app.features.events.api.v1.dto.EventRsvpDto
import com.hanmaum.dn.app.features.events.api.v1.dto.EventRsvpResponseDto
import com.hanmaum.dn.app.features.events.domain.EventRsvp
import com.hanmaum.dn.app.features.events.domain.EventRsvpLog
import java.time.ZoneOffset

fun EventRsvp.toDto(): EventRsvpDto =
    EventRsvpDto(
        publicId = publicId.toString(),
        title = title,
        windowStart = windowStart,
        windowEnd = windowEnd,
        isActive = isActive,
        announcementPublicId = announcement?.publicId?.toString(),
    )

fun EventRsvp.toActiveDto(response: EventRsvpLog?): ActiveEventRsvpDto =
    ActiveEventRsvpDto(
        publicId = publicId.toString(),
        title = title,
        windowStart = windowStart,
        windowEnd = windowEnd,
        announcementId = announcement?.publicId,
        myStatus = response?.status,
        respondedAt = response?.checkedInAt?.atOffset(ZoneOffset.UTC),
    )

fun EventRsvpLog.toResponseDto(): EventRsvpResponseDto =
    EventRsvpResponseDto(
        eventPublicId = eventRsvp.publicId.toString(),
        eventTitle = eventRsvp.title,
        status = status,
        respondedAt = checkedInAt.atOffset(ZoneOffset.UTC),
    )

fun EventRsvpLog.toAttendeeDto(): EventAttendeeDto =
    EventAttendeeDto(
        memberName = member.getFullName(),
        groupName = groupAtRsvp?.name,
        groupDivision = groupAtRsvp?.division,
        checkedInAt = checkedInAt.atOffset(ZoneOffset.UTC),
        status = status,
    )
