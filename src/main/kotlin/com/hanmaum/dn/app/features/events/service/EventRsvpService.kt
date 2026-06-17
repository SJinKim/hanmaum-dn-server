package com.hanmaum.dn.app.features.events.service

import com.hanmaum.dn.app.common.domainvalue.AnnouncementCategory
import com.hanmaum.dn.app.features.announcements.repository.AnnouncementRepository
import com.hanmaum.dn.app.features.events.api.toActiveDto
import com.hanmaum.dn.app.features.events.api.toDto
import com.hanmaum.dn.app.features.events.api.v1.dto.ActiveEventRsvpDto
import com.hanmaum.dn.app.features.events.api.v1.dto.CreateEventRsvpRequest
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

    // checkIn and getAttendees are added in Task 4
}
