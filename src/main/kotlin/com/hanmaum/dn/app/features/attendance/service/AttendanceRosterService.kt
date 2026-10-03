package com.hanmaum.dn.app.features.attendance.service

import com.hanmaum.dn.app.common.domainvalue.CheckInPresence
import com.hanmaum.dn.app.features.attendance.api.v1.dto.AttendanceLogResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.CreateAttendanceLogRequest
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import com.hanmaum.dn.app.features.attendance.repository.AttendanceDefinitionRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * The admin 체크인 명단: who checked in, person by person (#224).
 *
 * Kept apart from [AttendanceService] on purpose. That service only ever hands out
 * 순 aggregates; everything that names a member lives here, behind ADMIN.
 */
@Service
class AttendanceRosterService(
    private val definitionRepo: AttendanceDefinitionRepository,
    private val logRepo: AttendanceLogRepository,
    private val memberRepo: MemberRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun getRoster(
        date: LocalDate,
        definitionPublicId: UUID?,
    ): List<AttendanceLogResponse> {
        val definitionId =
            definitionPublicId?.let {
                definitionRepo
                    .findByPublicIdAndDeletedAtIsNull(it)
                    .orElseThrow { EntityNotFoundException("AttendanceDefinition not found: $it") }
                    .id
            }
        return logRepo.findRoster(date, definitionId).map { it.toResponse() }
    }

    /**
     * Records a check-in the member could not make themselves. The 순 is the member's
     * current one and the presence is UNCONFIRMED, because nobody measured a position.
     */
    @Transactional
    fun addLog(request: CreateAttendanceLogRequest): AttendanceLogResponse {
        val member =
            memberRepo
                .findByPublicIdAndDeletedAtIsNull(request.memberId)
                .orElseThrow { EntityNotFoundException("Member not found: ${request.memberId}") }
        val definition =
            definitionRepo
                .findByPublicIdAndDeletedAtIsNull(request.definitionId)
                .orElseThrow { EntityNotFoundException("AttendanceDefinition not found: ${request.definitionId}") }

        if (request.date.isAfter(LocalDate.now(clock))) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "미래 날짜에는 출석을 추가할 수 없습니다.")
        }
        if (request.date.dayOfWeek != definition.dayOfWeek) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "출석 정의의 요일과 날짜가 맞지 않습니다.")
        }

        val publicId = UUID.randomUUID()
        val inserted =
            logRepo.insertIfAbsent(
                publicId = publicId,
                definitionId = definition.id!!,
                memberId = member.id!!,
                groupId = member.group?.id,
                attendanceDate = request.date,
                presence = CheckInPresence.UNCONFIRMED.name,
            )
        if (inserted == 0) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 출석이 기록되어 있습니다.")
        }

        log.info("Attendance log added by admin definitionId={} date={}", definition.id, request.date)

        // The native insert bypassed the persistence context, so read the row back for its timestamp.
        return logRepo
            .findByPublicId(publicId)
            .orElseThrow { IllegalStateException("Inserted attendance log not found: $publicId") }
            .toResponse()
    }

    private fun AttendanceLog.toResponse() =
        AttendanceLogResponse(
            logPublicId = publicId.toString(),
            memberPublicId = member?.publicId?.toString(),
            fullName = member?.getFullName(),
            groupPublicId = groupAtCheckIn?.publicId?.toString(),
            groupName = groupAtCheckIn?.name,
            definitionPublicId = definition.publicId.toString(),
            definitionTitle = definition.title,
            checkedInAt = createdAt!!,
            presence = presence,
        )
}
