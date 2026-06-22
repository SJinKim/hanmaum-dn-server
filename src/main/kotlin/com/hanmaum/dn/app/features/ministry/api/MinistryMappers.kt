package com.hanmaum.dn.app.features.ministry.api

import com.hanmaum.dn.app.features.ministry.api.v1.dto.ActiveMinistryMemberDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.CreateMinistryRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryContactDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryContactRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryScheduleDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryScheduleRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistrySummaryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.UpdateMinistryRequest
import com.hanmaum.dn.app.features.ministry.domain.Ministry
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment
import com.hanmaum.dn.app.features.ministry.domain.MinistryContact
import com.hanmaum.dn.app.features.ministry.domain.MinistrySchedule
import com.hanmaum.dn.app.features.ministry.repository.ActiveMemberView

// ─── Entity creation ──────────────────────────────────────────────────────────

fun CreateMinistryRequest.toEntity(): Ministry =
    Ministry(
        name = this.title,
        shortDescription = this.subtitle,
        longDescription = this.about,
        imageUrl = this.imageUrl,
        isMinistryActive = true,
    ).also { ministry ->
        ministry.replaceRequirements(this.requirements)
        ministry.replaceSchedules(this.schedules.map { it.toDomain() })
        ministry.replaceContacts(this.contacts.map { it.toDomain() })
    }

// ─── PATCH update ─────────────────────────────────────────────────────────────

/** Applies only non-null fields (PATCH semantics). */
fun Ministry.applyPatch(request: UpdateMinistryRequest) {
    request.title?.let { this.name = it }
    request.subtitle?.let { this.shortDescription = it }
    request.about?.let { this.longDescription = it }
    request.requirements?.let { this.replaceRequirements(it) }
    request.schedules?.let { this.replaceSchedules(it.map(MinistryScheduleRequest::toDomain)) }
    request.contacts?.let { this.replaceContacts(it.map(MinistryContactRequest::toDomain)) }
    request.imageUrl?.let { this.imageUrl = it }
    request.isActive?.let { this.isMinistryActive = it }
}

// ─── Entity → DTO mappings ────────────────────────────────────────────────────

fun Ministry.toSummaryDto(): MinistrySummaryDto =
    MinistrySummaryDto(
        publicId = this.publicId.toString(),
        title = this.name,
        subtitle = this.shortDescription,
        imageUrl = this.imageUrl,
        contacts = this.contacts.map { it.toDto() },
        isActive = this.isMinistryActive,
    )

fun Ministry.toDto(): MinistryDto =
    MinistryDto(
        publicId = this.publicId.toString(),
        title = this.name,
        subtitle = this.shortDescription,
        about = this.longDescription,
        requirements = this.requirements.toList(),
        schedules = this.schedules.map { it.toDto() },
        contacts = this.contacts.map { it.toDto() },
        imageUrl = this.imageUrl,
        isActive = this.isMinistryActive,
    )

fun MinistryContactRequest.toDomain(): MinistryContact =
    MinistryContact(
        role = this.role,
        name = this.name,
    )

fun MinistryContact.toDto(): MinistryContactDto =
    MinistryContactDto(
        role = this.role,
        name = this.name,
    )

fun MinistryScheduleRequest.toDomain(): MinistrySchedule =
    MinistrySchedule(
        description = this.description,
        startTime = this.startTime,
        endTime = this.endTime,
    )

fun MinistrySchedule.toDto(): MinistryScheduleDto =
    MinistryScheduleDto(
        description = this.description,
        startTime = this.startTime,
        endTime = this.endTime,
    )

fun ActiveMemberView.toDto(): ActiveMinistryMemberDto =
    ActiveMinistryMemberDto(
        publicId = this.memberPublicId.toString(),
        fullName = this.fullName,
        startDate = this.startDate.toString(),
        note = this.note,
        gender = this.gender?.name,
    )

/** Maps a freshly created assignment to the same shape the active-members table renders. */
fun MinistryAssignment.toActiveMemberDto(): ActiveMinistryMemberDto =
    ActiveMinistryMemberDto(
        publicId = this.member.publicId.toString(),
        fullName = this.member.getFullName(),
        startDate = this.startDate.toString(),
        note = this.note,
        gender = this.member.gender?.name,
    )
