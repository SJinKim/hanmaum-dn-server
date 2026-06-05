package com.hanmaum.dn.app.features.members.api

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberDto
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberResponse
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberSummaryDto
import com.hanmaum.dn.app.features.members.api.v1.dto.MinistryHistoryDto
import com.hanmaum.dn.app.features.members.api.v1.dto.SummaryTrainingDto
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UserTrainingDto
import com.hanmaum.dn.app.features.members.domain.Member

// ─── Private helpers ──────────────────────────────────────────────────────────

private fun mapGender(value: String?): Gender? =
    when (value?.uppercase()) {
        "M", "남", "MALE" -> Gender.M
        "F", "여", "FEMALE" -> Gender.F
        else -> null
    }

private fun mapBaptism(value: String?): Baptism? =
    if (value.isNullOrBlank()) {
        null
    } else {
        try {
            Baptism.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

private fun mapStatus(value: String?): MemberStatus? =
    if (value.isNullOrBlank()) {
        null
    } else {
        try {
            MemberStatus.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }
    }

// ─── Entity creation ──────────────────────────────────────────────────────────

fun CreateMemberRequest.toEntity(): Member =
    Member(
        lastName = this.lastName,
        firstName = this.firstName,
        discriminator = this.discriminator,
        gender = mapGender(this.gender),
        birthDate = this.birthDate,
        phoneNumber = this.phoneNumber,
        email = this.email,
        street = this.street,
        zipCode = this.zipCode,
        city = this.city,
        registrationDate = this.registrationDate,
        churchRole = this.churchRole,
        baptism = mapBaptism(this.baptism),
        profileImageUrl = this.profileImageUrl,
    )

// ─── PATCH update ─────────────────────────────────────────────────────────────

/**
 * Applies only non-null fields from [request] onto the entity (PATCH semantics).
 * Status transitions: PENDING → ACTIVE (approve), ACTIVE ↔ INACTIVE allowed.
 * DELETED is terminal — use DELETE endpoint.
 *
 * @throws IllegalStateException  if member is already DELETED
 * @throws IllegalStateException  if request tries to set status to DELETED
 * @throws IllegalArgumentException if status string is not a valid MemberStatus value
 */
fun Member.applyPatch(request: UpdateMemberRequest) {
    if (this.memberStatus == MemberStatus.DELETED) {
        throw IllegalStateException("Cannot update a DELETED member.")
    }
    request.lastName?.let { this.lastName = it }
    request.firstName?.let { this.firstName = it }
    request.discriminator?.let { this.discriminator = it }
    request.gender?.let { this.gender = mapGender(it) }
    request.birthDate?.let { this.birthDate = it }
    request.phoneNumber?.let { this.phoneNumber = it }
    request.email?.let { this.email = it }
    request.street?.let { this.street = it }
    request.zipCode?.let { this.zipCode = it }
    request.city?.let { this.city = it }
    request.registrationDate?.let { this.registrationDate = it }
    request.churchRole?.let { this.churchRole = it }
    request.baptism?.let { this.baptism = mapBaptism(it) }
    request.profileImageUrl?.let { this.profileImageUrl = it }

    request.memberStatus?.let { statusStr ->
        val target =
            mapStatus(statusStr)
                ?: throw IllegalArgumentException("Unknown memberStatus value: $statusStr")
        if (target == MemberStatus.DELETED) {
            throw IllegalStateException("Use the DELETE endpoint to soft-delete a member.")
        }
        this.memberStatus = target
    }
}

// ─── Entity → DTO mappings ────────────────────────────────────────────────────

/**
 * Full detail DTO. publicId (UUID string) is the only external identifier; internal Long id is never returned.
 * [trainings] and [ministries] are enriched by the service (default empty for create/update responses).
 */
fun Member.toDto(
    trainings: List<UserTrainingDto> = emptyList(),
    ministries: List<MinistryHistoryDto> = emptyList(),
): MemberDto =
    MemberDto(
        publicId = this.publicId.toString(),
        lastName = this.lastName,
        firstName = this.firstName,
        discriminator = this.discriminator,
        gender = this.gender?.name,
        baptism = this.baptism?.name,
        birthDate = this.birthDate,
        phoneNumber = this.phoneNumber,
        email = this.email,
        street = this.street,
        zipCode = this.zipCode,
        city = this.city,
        registrationDate = this.registrationDate,
        memberStatus = this.memberStatus.name,
        churchRole = this.churchRole,
        groupName = this.group?.name,
        profileImageUrl = this.profileImageUrl,
        trainings = trainings,
        ministries = ministries,
    )

/**
 * Lightweight list DTO. [latestTraining] / [trainings] / [activeMinistries] are derived per page
 * by the service (batched, no N+1) and passed in here.
 */
fun Member.toSummaryDto(
    latestTraining: String? = null,
    trainings: List<SummaryTrainingDto> = emptyList(),
    activeMinistries: List<String> = emptyList(),
): MemberSummaryDto =
    MemberSummaryDto(
        publicId = this.publicId.toString(),
        lastName = this.lastName,
        firstName = this.firstName,
        email = this.email,
        memberStatus = this.memberStatus.name,
        baptism = this.baptism?.name,
        groupName = this.group?.name,
        updatedAt = this.updatedAt,
        latestTraining = latestTraining,
        trainings = trainings,
        activeMinistries = activeMinistries,
    )

fun Member.toResponse(): MemberResponse =
    MemberResponse(
        publicId = this.publicId.toString(),
        firstName = this.firstName,
        lastName = this.lastName,
        email = this.email,
        status = this.memberStatus,
        churchRole = this.churchRole,
        groupName = this.group?.name,
        city = this.city,
        phoneNumber = this.phoneNumber,
        profileImageUrl = this.profileImageUrl,
    )
