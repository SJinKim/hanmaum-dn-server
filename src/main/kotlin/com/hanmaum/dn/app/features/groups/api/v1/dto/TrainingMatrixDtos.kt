package com.hanmaum.dn.app.features.groups.api.v1.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberSummaryDto
import com.hanmaum.dn.app.features.members.api.v1.dto.SummaryTrainingDto
import dev.zacsweers.redacted.annotations.Redacted
import dev.zacsweers.redacted.annotations.Unredacted
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

/** Only the fields needed to render the 양육 matrix; no contact, account or ministry data. */
@Redacted
data class TrainingMatrixMemberDto(
    @Unredacted val publicId: String,
    val lastName: String,
    val firstName: String,
    @Unredacted val memberStatus: String,
    val baptism: String?,
    @Unredacted val groupPublicId: String?,
    @Unredacted val groupName: String?,
    @Unredacted val trainings: List<SummaryTrainingDto>,
    @get:JsonProperty("isNextGroupLeader")
    @Unredacted val isNextGroupLeader: Boolean,
    @Unredacted val oneOnOneSignupFilled: Boolean,
    @get:JsonProperty("isGroupLeader")
    @Unredacted val isGroupLeader: Boolean,
    @Unredacted val groupLeaderSince: LocalDate?,
) {
    companion object {
        fun from(member: MemberSummaryDto) =
            TrainingMatrixMemberDto(
                member.publicId,
                member.lastName,
                member.firstName,
                member.memberStatus,
                member.baptism,
                member.groupPublicId,
                member.groupName,
                member.trainings,
                member.isNextGroupLeader,
                member.oneOnOneSignupFilled,
                member.isGroupLeader,
                member.groupLeaderSince,
            )
    }
}

/** Set the 예비순장 flag only; omitted/null is invalid rather than an implicit false. */
data class UpdateNextGroupLeaderRequest(
    @field:NotNull
    @get:Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Required explicit 예비순장 value; null is invalid.")
    @get:JsonProperty("isNextGroupLeader")
    val isNextGroupLeader: Boolean?,
)

/** A flag write never returns the member's administrative detail. */
data class NextGroupLeaderResponse(
    val publicId: String,
    @get:JsonProperty("isNextGroupLeader")
    val isNextGroupLeader: Boolean,
)
