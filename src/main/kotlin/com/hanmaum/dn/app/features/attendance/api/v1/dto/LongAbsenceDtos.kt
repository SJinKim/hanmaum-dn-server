package com.hanmaum.dn.app.features.attendance.api.v1.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.hanmaum.dn.app.features.attendance.domain.AbsenceChangeKind
import com.hanmaum.dn.app.features.attendance.domain.AbsenceChangeOrigin
import com.hanmaum.dn.app.features.attendance.domain.AbsencePhaseEndReason
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
    @field:Schema(description = "Stored phase of this absence; null while nothing was recorded for it.")
    val phasePublicId: UUID?,
    val fullName: String,
    val discriminator: String?,
    val group: LongAbsenceGroupDto?,
    @field:Schema(description = "Date of the last qualifying Sunday check-in. Send back unchanged on writes; never a manual return date.")
    val lastSundayDate: LocalDate,
    @field:Schema(description = "Date the absence counts from: the later of lastSundayDate and a manual return.")
    val absenceAnchorDate: LocalDate,
    @field:Schema(description = "First day of the absence, the day after absenceAnchorDate.")
    val absentSince: LocalDate,
    @field:Schema(description = "Calendar days since absenceAnchorDate, today included.")
    val absenceDays: Long,
    @field:Schema(description = "Full calendar months since absenceAnchorDate; listed from 3, urgency steps at 6 and 12.")
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
    @field:Schema(
        description = "Opaque conflict token of the member's follow-up. Send back unchanged on writes; not necessarily 0 or consecutive.",
    )
    val version: Long,
) {
    override fun toString(): String =
        "LongAbsenceResponse(memberPublicId=$memberPublicId, lastSundayDate=$lastSundayDate, " +
            "absenceAnchorDate=$absenceAnchorDate, contacted=$contacted, version=$version)"
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

@Schema(description = "Replaces the memo of the current phase; null or blank clears it together with every earlier memo version.")
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

@Schema(description = "Marks the member of the current phase as back without a check-in. Date and actor are set by the server.")
data class LongAbsenceReturnRequest(
    @field:NotNull
    val lastSundayDate: LocalDate?,
    @field:NotNull
    @field:PositiveOrZero
    val version: Long?,
)

@Schema(description = "The phase a manual return has just ended.")
data class LongAbsenceReturnResponse(
    val memberPublicId: UUID,
    val phasePublicId: UUID,
    @field:Schema(description = "Europe/Berlin day of the return; the next absence counts from it.")
    val endedOn: LocalDate,
    val endReason: AbsencePhaseEndReason,
    val returnedAt: Instant,
    @field:Schema(description = "Account (Keycloak subject) that marked the member as back.")
    val returnedBy: String,
    val returnedByMember: LongAbsenceActorDto?,
    @field:Schema(description = "New conflict token of the member's follow-up.")
    val version: Long,
)

@Schema(description = "One closed absence phase. No memo and no phone number.")
data class LongAbsenceHistoryItem(
    val phasePublicId: UUID,
    val memberPublicId: UUID,
    @field:Schema(description = "Current name of the member.")
    val fullName: String,
    val discriminator: String?,
    val lastSundayDate: LocalDate,
    val absenceAnchorDate: LocalDate,
    val absentSince: LocalDate,
    val endedOn: LocalDate,
    val endReason: AbsencePhaseEndReason,
    @field:Schema(description = "Contact status the phase ended with.")
    val contacted: Boolean,
    val contactedBy: String?,
    val contactedByMember: LongAbsenceActorDto?,
    val contactedAt: Instant?,
    @field:Schema(description = "Set for MANUAL_RETURN only.")
    val returnedAt: Instant?,
    @field:Schema(description = "Set for MANUAL_RETURN only.")
    val returnedBy: String?,
    val returnedByMember: LongAbsenceActorDto?,
)

@Schema(description = "Page of closed phases, newest end first.")
data class LongAbsenceHistoryPage(
    val items: List<LongAbsenceHistoryItem>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

@Schema(description = "One stored absence phase, open or closed.")
data class LongAbsencePhaseResponse(
    val phasePublicId: UUID,
    val memberPublicId: UUID,
    val fullName: String,
    val discriminator: String?,
    val lastSundayDate: LocalDate,
    val absenceAnchorDate: LocalDate,
    val absentSince: LocalDate,
    @field:Schema(description = "Null while the phase is open.")
    val endedOn: LocalDate?,
    @field:Schema(description = "Null while the phase is open.")
    val endReason: AbsencePhaseEndReason?,
    val contacted: Boolean,
    val contactedBy: String?,
    val contactedByMember: LongAbsenceActorDto?,
    val contactedAt: Instant?,
    val returnedAt: Instant?,
    val returnedBy: String?,
    val returnedByMember: LongAbsenceActorDto?,
    @field:Schema(
        maxLength = LONG_ABSENCE_MEMO_MAX_LENGTH,
        description = "Current memo; null when there is none or 90 days after the end.",
    )
    val memo: String?,
    @field:Schema(description = "True when this is the phase currently on the long-absence list.")
    @get:JsonProperty("isCurrent")
    val isCurrent: Boolean,
) {
    override fun toString(): String =
        "LongAbsencePhaseResponse(phasePublicId=$phasePublicId, endedOn=$endedOn, endReason=$endReason, isCurrent=$isCurrent)"
}

@Schema(description = "One contact or memo change of a phase; holds the new value. Only the payload of its kind is set.")
data class LongAbsenceChangeDto(
    val publicId: UUID,
    @field:Schema(description = "Conflict token of the write; a contact and a memo entry of one write share it.")
    val revision: Long,
    val kind: AbsenceChangeKind,
    val origin: AbsenceChangeOrigin,
    @field:Schema(description = "Null for a LEGACY_SNAPSHOT whose time is unknown.")
    val occurredAt: Instant?,
    @field:Schema(description = "Null for a LEGACY_SNAPSHOT whose actor is unknown.")
    val actorSubject: String?,
    val actorMember: LongAbsenceActorDto?,
    @field:Schema(description = "CONTACT_CHANGED only.")
    val contacted: Boolean?,
    @field:Schema(maxLength = LONG_ABSENCE_MEMO_MAX_LENGTH, description = "MEMO_CHANGED only.")
    val memo: String?,
) {
    override fun toString(): String = "LongAbsenceChangeDto(publicId=$publicId, revision=$revision, kind=$kind, origin=$origin)"
}

@Schema(description = "Page of changes of one phase, newest first.")
data class LongAbsenceChangePage(
    val items: List<LongAbsenceChangeDto>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    override fun toString(): String = "LongAbsenceChangePage(page=$page, size=$size, totalElements=$totalElements)"
}
