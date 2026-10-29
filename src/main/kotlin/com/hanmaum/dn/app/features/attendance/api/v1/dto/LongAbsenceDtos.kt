package com.hanmaum.dn.app.features.attendance.api.v1.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.hanmaum.dn.app.features.newcomers.domain.PostAssignmentAttendance
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Maximum memo length in UTF-16 code units, as counted by String.length and HTML maxlength. */
const val LONG_ABSENCE_MEMO_MAX_LENGTH = 2000

private const val MEMO_TOO_LONG = "memo must be at most $LONG_ABSENCE_MEMO_MAX_LENGTH characters"

// Free text in these classes may hold health or family details, so toString() never prints it.

@Schema(description = "One long-term absentee (장기결석자) and the follow-up of the current absence phase. No phone number.")
data class LongAbsenceResponse(
    val memberPublicId: UUID,
    val fullName: String,
    val discriminator: String?,
    val group: LongAbsenceGroupDto?,
    @field:Schema(description = "Date of the last qualifying Sunday check-in; identifies the absence phase.")
    val lastSundayDate: LocalDate,
    @field:Schema(description = "First day of the absence, the day after lastSundayDate.")
    val absentSince: LocalDate,
    @field:Schema(description = "Calendar days since lastSundayDate, today included.")
    val absenceDays: Long,
    @field:Schema(description = "Full calendar months since lastSundayDate; listed from 3, urgency steps at 6 and 12.")
    val absenceMonths: Long,
    @field:Schema(description = "Attendance noted in the member's former newcomer profile, if any.")
    val postAssignmentAttendance: PostAssignmentAttendance?,
    val contacted: Boolean,
    @field:Schema(description = "Account (Keycloak subject) that marked the phase as contacted.")
    val contactedBy: String?,
    @field:Schema(description = "The member behind contactedBy, when that account has a member profile.")
    val contactedByMember: LongAbsenceActorDto?,
    val contactedAt: Instant?,
    @field:Schema(maxLength = LONG_ABSENCE_MEMO_MAX_LENGTH)
    val memo: String?,
    @field:Schema(description = "Send back unchanged on writes; 0 while nothing was recorded for this phase.")
    val version: Long,
) {
    override fun toString(): String =
        "LongAbsenceResponse(memberPublicId=$memberPublicId, lastSundayDate=$lastSundayDate, contacted=$contacted, version=$version)"
}

data class LongAbsenceGroupDto(
    val publicId: UUID,
    val name: String,
    val division: String?,
)

data class LongAbsenceActorDto(
    val publicId: UUID,
    val fullName: String,
)

@Schema(description = "Counts for the home screen. Same candidates as the list, without names or memos.")
data class LongAbsenceSummaryResponse(
    val asOfDate: LocalDate,
    val totalCount: Long,
    val uncontactedCount: Long,
    @field:Schema(description = "Not contacted and absent for at least 6 calendar months.")
    val atLeastSixMonthsCount: Long,
)

@Schema(description = "Replaces the memo of the current phase; null or blank clears it.")
data class LongAbsenceMemoRequest(
    @field:NotNull
    val lastSundayDate: LocalDate?,
    @field:NotNull
    @field:PositiveOrZero
    val version: Long?,
    @field:Size(max = LONG_ABSENCE_MEMO_MAX_LENGTH, message = MEMO_TOO_LONG)
    @param:JsonProperty(required = true)
    @field:Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        types = ["string", "null"],
        maxLength = LONG_ABSENCE_MEMO_MAX_LENGTH,
    )
    val memo: String?,
) {
    override fun toString(): String = "LongAbsenceMemoRequest(lastSundayDate=$lastSundayDate, version=$version)"
}

@Schema(description = "Sets the contact status of the current phase; memoUpdate optionally changes the memo in the same write.")
data class LongAbsenceContactStatusRequest(
    @field:NotNull
    val lastSundayDate: LocalDate?,
    @field:NotNull
    @field:PositiveOrZero
    val version: Long?,
    @field:NotNull
    val contacted: Boolean?,
    @field:Valid
    @field:Schema(description = "Absent or null leaves the memo unchanged.")
    val memoUpdate: LongAbsenceMemoUpdate? = null,
) {
    override fun toString(): String =
        "LongAbsenceContactStatusRequest(lastSundayDate=$lastSundayDate, version=$version, contacted=$contacted, " +
            "memoUpdate=${memoUpdate?.let { "<present>" }})"
}

data class LongAbsenceMemoUpdate(
    @field:Size(max = LONG_ABSENCE_MEMO_MAX_LENGTH, message = MEMO_TOO_LONG)
    @param:JsonProperty(required = true)
    @field:Schema(
        requiredMode = Schema.RequiredMode.REQUIRED,
        types = ["string", "null"],
        maxLength = LONG_ABSENCE_MEMO_MAX_LENGTH,
        description = "New memo; null or blank clears it.",
    )
    val value: String?,
) {
    override fun toString(): String = "LongAbsenceMemoUpdate(<redacted>)"
}
