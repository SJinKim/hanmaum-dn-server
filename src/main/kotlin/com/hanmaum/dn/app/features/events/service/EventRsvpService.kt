package com.hanmaum.dn.app.features.events.service

import com.hanmaum.dn.app.common.domainvalue.AnnouncementCategory
import com.hanmaum.dn.app.features.announcements.repository.AnnouncementRepository
import com.hanmaum.dn.app.features.events.api.toActiveDto
import com.hanmaum.dn.app.features.events.api.toAttendeeDto
import com.hanmaum.dn.app.features.events.api.toDto
import com.hanmaum.dn.app.features.events.api.v1.dto.ActiveEventRsvpDto
import com.hanmaum.dn.app.features.events.api.v1.dto.CreateEventRsvpRequest
import com.hanmaum.dn.app.features.events.api.v1.dto.EventAttendeesResponse
import com.hanmaum.dn.app.features.events.api.v1.dto.EventCheckInResponse
import com.hanmaum.dn.app.features.events.api.v1.dto.EventRsvpDto
import com.hanmaum.dn.app.features.events.api.v1.dto.UpdateEventRsvpRequest
import com.hanmaum.dn.app.features.events.domain.EventRsvp
import com.hanmaum.dn.app.features.events.repository.EventRsvpLogRepository
import com.hanmaum.dn.app.features.events.repository.EventRsvpRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
class EventRsvpService(
    private val eventRsvpRepo: EventRsvpRepository,
    private val eventRsvpLogRepo: EventRsvpLogRepository,
    private val memberRepo: MemberRepository,
    private val announcementRepo: AnnouncementRepository,
    private val clock: Clock,
) {
    @Transactional
    fun createRsvp(req: CreateEventRsvpRequest): EventRsvpDto {
        if (!req.windowEnd.isAfter(req.windowStart)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "종료 시간은 시작 시간 이후여야 합니다.")
        }
        val announcement =
            req.announcementId?.let { id ->
                announcementRepo
                    .findByPublicIdAndDeleteEntryAtIsNull(id)
                    .orElseThrow { EntityNotFoundException("Announcement not found: $id") }
                    .also {
                        if (it.category != AnnouncementCategory.EVENT) {
                            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이벤트 카테고리의 공지만 연결할 수 있습니다.")
                        }
                    }
            }
        val rsvp =
            EventRsvp(
                announcement = announcement,
                title = req.title,
                windowStart = req.windowStart,
                windowEnd = req.windowEnd,
            )
        return eventRsvpRepo.save(rsvp).toDto()
    }

    @Transactional
    fun updateRsvp(
        publicId: UUID,
        req: UpdateEventRsvpRequest,
    ): EventRsvpDto {
        val rsvp =
            eventRsvpRepo
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("EventRsvp not found: $publicId") }
        val effectiveStart = req.windowStart ?: rsvp.windowStart
        val effectiveEnd = req.windowEnd ?: rsvp.windowEnd
        if (!effectiveEnd.isAfter(effectiveStart)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "종료 시간은 시작 시간 이후여야 합니다.")
        }
        req.title?.let { rsvp.title = it }
        req.windowStart?.let { rsvp.windowStart = it }
        req.windowEnd?.let { rsvp.windowEnd = it }
        req.isActive?.let { rsvp.isActive = it }
        req.announcementId?.let { id ->
            rsvp.announcement =
                announcementRepo
                    .findByPublicIdAndDeleteEntryAtIsNull(id)
                    .orElseThrow { EntityNotFoundException("Announcement not found: $id") }
                    .also {
                        if (it.category != AnnouncementCategory.EVENT) {
                            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이벤트 카테고리의 공지만 연결할 수 있습니다.")
                        }
                    }
        }
        return rsvp.toDto()
    }

    @Transactional
    fun deactivateRsvp(publicId: UUID) {
        val rsvp =
            eventRsvpRepo
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("EventRsvp not found: $publicId") }
        rsvp.isActive = false
    }

    @Transactional(readOnly = true)
    fun getActiveRsvps(): List<ActiveEventRsvpDto> = eventRsvpRepo.findActiveNow(OffsetDateTime.now(clock)).map { it.toActiveDto() }

    @Transactional(readOnly = true)
    fun listAllRsvps(): List<EventRsvpDto> = eventRsvpRepo.findAllNotDeleted().map { it.toDto() }

    @Transactional
    fun checkIn(
        publicId: UUID,
        keycloakSub: String,
    ): EventCheckInResponse {
        val member =
            memberRepo.findByKeycloakIdAndDeletedAtIsNull(keycloakSub)
                ?: throw EntityNotFoundException("Member not found for subject: $keycloakSub")
        val rsvp =
            eventRsvpRepo
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("EventRsvp not found: $publicId") }
        if (!rsvp.isActive) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "비활성화된 RSVP입니다.")
        }
        val now = OffsetDateTime.now(clock)
        if (now.isBefore(rsvp.windowStart) || !now.isBefore(rsvp.windowEnd)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 RSVP 신청 기간이 아닙니다.")
        }
        val inserted =
            eventRsvpLogRepo.insertIfAbsent(
                publicId = UUID.randomUUID(),
                eventRsvpId = rsvp.id!!,
                memberId = member.id!!,
                groupId = member.group?.id,
                checkedInAt = now.toInstant(),
            )
        if (inserted == 0) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 RSVP 신청했습니다.")
        }
        return EventCheckInResponse(
            eventPublicId = rsvp.publicId.toString(),
            eventTitle = rsvp.title,
            checkedInAt = now,
        )
    }

    @Transactional(readOnly = true)
    fun getAttendees(publicId: UUID): EventAttendeesResponse {
        val rsvp =
            eventRsvpRepo
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("EventRsvp not found: $publicId") }
        val logs = eventRsvpLogRepo.findAttendeesWithDetails(rsvp.id!!)
        return EventAttendeesResponse(
            eventPublicId = rsvp.publicId.toString(),
            eventTitle = rsvp.title,
            totalCount = logs.size,
            attendees = logs.map { it.toAttendeeDto() },
        )
    }
}
