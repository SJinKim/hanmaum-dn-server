package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.features.newcomers.api.v1.dto.CreateNewcomerVisitRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerVisitDayCount
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerVisitResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerVisitSourceCount
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerVisitStatsResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.UpdateNewcomerVisitRequest
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerLifecycle
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerVisit
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerVisitSource
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerVisitType
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerVisitRepository
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * 방문 기록 (#261): the quick capture of who came on the day, and the funnel the 새가족 team
 * evaluates on it. The rows are history; deleting is soft so the counts stay explainable.
 */
@Service
class NewcomerVisitService(
    private val visitRepo: NewcomerVisitRepository,
    private val profileRepo: NewcomerProfileRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * The history, a page at a time (#265). Without bounds it is everything; one bound alone is
     * open on the other side. Unlike [stats] there is no one-year cap.
     */
    @Transactional(readOnly = true)
    fun list(
        from: LocalDate?,
        to: LocalDate?,
        page: Int,
        size: Int,
    ): Page<NewcomerVisitResponse> {
        if (page < 0 || size !in 1..100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be non-negative and size must be between 1 and 100.")
        }
        val start = from ?: EARLIEST
        val end = to ?: today()
        if (start.isAfter(end)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "시작일이 종료일보다 늦습니다.")
        }
        return visitRepo.findPageInRange(start, end, PageRequest.of(page, size)).map { it.toResponse() }
    }

    @Transactional
    fun create(request: CreateNewcomerVisitRequest): NewcomerVisitResponse {
        val visitDate = request.visitDate ?: today()
        requireNotFuture(visitDate)
        val visit =
            NewcomerVisit(
                visitDate = visitDate,
                lastName = request.lastName.trim(),
                firstName = request.firstName.trim(),
                gender = request.gender,
                birthYear = request.birthYear,
                visitType = request.visitType,
                source = request.source,
                note = request.note?.trim()?.ifEmpty { null },
            )
        val saved = visitRepo.save(visit)
        log.info("Newcomer visit recorded visitDate={} visitType={}", saved.visitDate, saved.visitType)
        return saved.toResponse()
    }

    @Transactional
    fun update(
        publicId: UUID,
        request: UpdateNewcomerVisitRequest,
    ): NewcomerVisitResponse {
        val visit = find(publicId)
        request.visitDate?.let {
            requireNotFuture(it)
            visit.visitDate = it
        }
        request.lastName?.let { visit.lastName = requireName(it) }
        request.firstName?.let { visit.firstName = requireName(it) }
        request.gender?.let { visit.gender = it }
        request.birthYear?.let { visit.birthYear = it }
        request.visitType?.let { visit.visitType = it }
        request.source?.let { visit.source = it }
        request.note?.let { visit.note = it.trim().ifEmpty { null } }
        return visit.toResponse()
    }

    @Transactional
    fun softDelete(publicId: UUID) {
        val visit = find(publicId)
        visit.deletedAt = Instant.now(clock)
        log.info("Newcomer visit deleted id={}", visit.id)
    }

    @Transactional
    fun linkProfile(
        publicId: UUID,
        newcomerPublicId: UUID?,
    ): NewcomerVisitResponse {
        val visit = find(publicId)
        visit.newcomerProfile =
            newcomerPublicId?.let {
                profileRepo
                    .findByPublicIdAndDeletedAtIsNull(it)
                    .orElseThrow { EntityNotFoundException("Newcomer not found: $it") }
            }
        log.info("Newcomer visit id={} linked={}", visit.id, visit.newcomerProfile?.id)
        return visit.toResponse()
    }

    @Transactional(readOnly = true)
    fun stats(
        from: LocalDate?,
        to: LocalDate?,
    ): NewcomerVisitStatsResponse {
        val (start, end) = range(from, to)
        val visits = visitRepo.findInRange(start, end)
        val bySource =
            visits
                .groupingBy { it.source }
                .eachCount()
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<NewcomerVisitSource?, Int>> { it.value }.thenBy { it.key?.ordinal ?: Int.MAX_VALUE },
                ).map { NewcomerVisitSourceCount(it.key, it.value) }
        val byDay =
            visits
                .groupingBy { it.visitDate }
                .eachCount()
                .toSortedMap()
                .map { (date, count) -> NewcomerVisitDayCount(date, count) }
        return NewcomerVisitStatsResponse(
            from = start,
            to = end,
            visits = visits.size,
            firstVisits = visits.count { it.visitType == NewcomerVisitType.FIRST },
            revisits = visits.count { it.visitType == NewcomerVisitType.REVISIT },
            registered = visits.count { it.newcomerProfile?.isNotDeleted() == true },
            graduated =
                visits.count {
                    it.newcomerProfile
                        ?.takeIf { p ->
                            p.isNotDeleted()
                        }?.lifecycleStatus == NewcomerLifecycle.GRADUATED
                },
            bySource = bySource,
            byDay = byDay,
        )
    }

    private fun find(publicId: UUID): NewcomerVisit =
        visitRepo.findActive(publicId).orElseThrow { EntityNotFoundException("NewcomerVisit not found: $publicId") }

    private fun today(): LocalDate = LocalDate.now(clock)

    /** Without bounds the range is today; one bound alone is a single day. A year is the most. */
    private fun range(
        from: LocalDate?,
        to: LocalDate?,
    ): Pair<LocalDate, LocalDate> {
        val start = from ?: to ?: today()
        val end = to ?: from ?: today()
        if (start.isAfter(end)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "시작일이 종료일보다 늦습니다.")
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 기간은 최대 1년입니다.")
        }
        return start to end
    }

    private fun requireNotFuture(date: LocalDate) {
        if (date.isAfter(today())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "미래 날짜에는 방문을 기록할 수 없습니다.")
        }
    }

    private fun requireName(value: String): String =
        value.trim().ifEmpty { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "이름을 입력해 주세요.") }

    private fun NewcomerVisit.toResponse(): NewcomerVisitResponse {
        val profile = newcomerProfile?.takeIf { it.isNotDeleted() }
        return NewcomerVisitResponse(
            publicId = publicId.toString(),
            visitDate = visitDate,
            lastName = lastName,
            firstName = firstName,
            fullName = lastName + firstName,
            gender = gender,
            birthYear = birthYear,
            visitType = visitType,
            source = source,
            note = note,
            newcomerPublicId = profile?.publicId?.toString(),
            newcomerLifecycle = profile?.lifecycleStatus,
            createdAt = createdAt ?: Instant.now(clock),
        )
    }

    private companion object {
        const val MAX_RANGE_DAYS = 365L

        /** Lower bound of an open list; no visit predates the app. */
        val EARLIEST: LocalDate = LocalDate.of(2000, 1, 1)
    }
}
