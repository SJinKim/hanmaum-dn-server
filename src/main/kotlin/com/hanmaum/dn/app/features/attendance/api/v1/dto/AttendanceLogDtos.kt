package com.hanmaum.dn.app.features.attendance.api.v1.dto

import com.hanmaum.dn.app.common.domainvalue.CheckInPresence
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** One row of the admin 체크인 명단: who checked in, for what, when, and from where. */
data class AttendanceLogResponse(
    val logPublicId: String,
    /** Null once the member was purged; the row then only counts towards its group. */
    val memberPublicId: String?,
    /** Null for the same reason as [memberPublicId]. */
    val fullName: String?,
    /** The member's 순 at check-in, not the current one. Null for members without a group. */
    val groupPublicId: String?,
    val groupName: String?,
    val definitionPublicId: String,
    val definitionTitle: String,
    val checkedInAt: Instant,
    val presence: CheckInPresence,
)

/**
 * Body for the admin's manual 추가. The server records the member's current 순 and
 * [CheckInPresence.UNCONFIRMED], because nobody measured where the member was.
 */
data class CreateAttendanceLogRequest(
    @field:NotNull(message = "청년은 필수입니다.")
    val memberId: UUID,
    @field:NotNull(message = "출석 정의는 필수입니다.")
    val definitionId: UUID,
    @field:NotNull(message = "날짜는 필수입니다.")
    val date: LocalDate,
)
