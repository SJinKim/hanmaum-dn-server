package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.features.attendance.api.toDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceCheckInRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceCheckInResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceGroupCountsResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.ChurchGroupAttendanceCountResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.DefinitionDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.UpdateDefinitionRequest
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.repository.AttendanceDefinitionRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.church.service.ChurchGeofenceService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class AttendanceService(
    private val definitionRepo: AttendanceDefinitionRepository,
    private val logRepo: AttendanceLogRepository,
    private val memberRepo: MemberRepository,
    private val churchGeofenceService: ChurchGeofenceService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(AttendanceService::class.java)

    // ─── Definition CRUD ───────────────────────────────────────────────────────

    @Transactional
    fun createDefinition(req: CreateDefinitionRequest): DefinitionDto {
        if (!req.windowEnd.isAfter(req.windowStart)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "종료 시간은 시작 시간 이후여야 합니다.")
        }
        val definition =
            AttendanceDefinition(
                title = req.title,
                dayOfWeek = req.dayOfWeek,
                windowStart = req.windowStart,
                windowEnd = req.windowEnd,
            )
        return definitionRepo.save(definition).toDto()
    }

    @Transactional(readOnly = true)
    fun getDefinitions(activeOnly: Boolean): List<DefinitionDto> = definitionRepo.findAll(activeOnly).map { it.toDto() }

    @Transactional
    fun updateDefinition(
        publicId: UUID,
        req: UpdateDefinitionRequest,
    ): DefinitionDto {
        val definition =
            definitionRepo
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("AttendanceDefinition not found: $publicId") }

        req.title?.let { definition.title = it }
        req.dayOfWeek?.let { definition.dayOfWeek = it }
        req.windowStart?.let { definition.windowStart = it }
        req.windowEnd?.let { definition.windowEnd = it }
        req.isActive?.let { definition.isActive = it }

        val effectiveStart = req.windowStart ?: definition.windowStart
        val effectiveEnd = req.windowEnd ?: definition.windowEnd
        if (!effectiveEnd.isAfter(effectiveStart)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "종료 시간은 시작 시간 이후여야 합니다.")
        }

        return definition.toDto()
    }

    @Transactional
    fun deactivateDefinition(publicId: UUID) {
        val definition =
            definitionRepo
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("AttendanceDefinition not found: $publicId") }
        definition.isActive = false
    }

    // ─── Check-in ─────────────────────────────────────────────────────────────

    @Transactional
    fun checkIn(
        keycloakSubject: String,
        request: AttendanceCheckInRequest? = null,
    ): AttendanceCheckInResponse {
        val member =
            memberRepo.findByKeycloakIdAndDeletedAtIsNull(keycloakSubject)
                ?: throw EntityNotFoundException("Member not found for subject: $keycloakSubject")

        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()
        val currentTime = now.toLocalTime()
        val currentDay = now.dayOfWeek

        val matchingDefinition =
            definitionRepo
                .findByDayOfWeekAndIsActiveTrueAndDeletedAtIsNull(currentDay)
                .firstOrNull { def ->
                    !currentTime.isBefore(def.windowStart) && currentTime.isBefore(def.windowEnd)
                } ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "현재 활성화된 출석 체크인 시간이 없습니다.",
            )

        // Evaluated before the insert so the verdict is stored with the row it describes.
        // A missing body, an unconfigured geofence and a fix too vague to judge all land on
        // UNCONFIRMED — none of them is a reason to refuse the check-in.
        val presence =
            churchGeofenceService.evaluate(
                latitude = request?.latitude,
                longitude = request?.longitude,
                accuracyMeters = request?.accuracyMeters,
            )

        val inserted =
            logRepo.insertIfAbsent(
                publicId = UUID.randomUUID(),
                definitionId = matchingDefinition.id!!,
                memberId = member.id!!,
                groupId = member.group?.id,
                attendanceDate = today,
                presence = presence.name,
            )
        if (inserted == 0) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 오늘 출석 체크인 했습니다.")
        }

        log.info(
            "Attendance check-in recorded definitionId={} presence={}",
            matchingDefinition.id,
            presence,
        )

        return AttendanceCheckInResponse(
            definitionPublicId = matchingDefinition.publicId.toString(),
            definitionTitle = matchingDefinition.title,
            attendanceDate = today,
            presence = presence,
        )
    }

    @Transactional(readOnly = true)
    fun getGroupCounts(
        definitionPublicId: UUID,
        attendanceDate: LocalDate,
    ): AttendanceGroupCountsResponse {
        val definition =
            definitionRepo
                .findByPublicIdAndDeletedAtIsNull(definitionPublicId)
                .orElseThrow { EntityNotFoundException("AttendanceDefinition not found: $definitionPublicId") }

        val groups =
            logRepo
                .countByChurchGroup(definition.id!!, attendanceDate)
                .map { count ->
                    ChurchGroupAttendanceCountResponse(
                        groupPublicId = count.groupPublicId?.toString(),
                        groupDivision = count.groupDivision,
                        groupName = count.groupName,
                        attendanceCount = count.attendanceCount,
                        inPlaceCount = count.inPlaceCount,
                        outsideCount = count.outsideCount,
                        unconfirmedCount = count.unconfirmedCount,
                    )
                }

        return AttendanceGroupCountsResponse(
            definitionPublicId = definition.publicId.toString(),
            definitionTitle = definition.title,
            attendanceDate = attendanceDate,
            totalCount = groups.sumOf { it.attendanceCount },
            totalInPlaceCount = groups.sumOf { it.inPlaceCount },
            totalOutsideCount = groups.sumOf { it.outsideCount },
            totalUnconfirmedCount = groups.sumOf { it.unconfirmedCount },
            groups = groups,
        )
    }
}
