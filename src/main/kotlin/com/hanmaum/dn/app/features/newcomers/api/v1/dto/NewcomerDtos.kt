package com.hanmaum.dn.app.features.newcomers.api.v1.dto

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.features.newcomers.domain.ChurchExperience
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerIdentityStatus
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerLifecycle
import com.hanmaum.dn.app.features.newcomers.domain.PostAssignmentAttendance
import dev.zacsweers.redacted.annotations.Redacted
import dev.zacsweers.redacted.annotations.Unredacted
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate

@Redacted
data class CreateNewcomerRequest(
    @field:NotBlank val lastName: String,
    @field:NotBlank val firstName: String,
    val englishName: String? = null,
    val gender: Gender? = null,
    val birthDate: LocalDate? = null,
    @field:Email val email: String? = null,
    @field:Size(max = 50) val phoneNumber: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val baptism: Baptism? = null,
    val profileImageUrl: String? = null,
    val registrationDate: LocalDate? = null,
    @field:Min(1) @field:Max(99) val intakeRound: Int? = null,
    @Unredacted val hasVisited: Boolean = false,
    @Unredacted val lifecycleStatus: NewcomerLifecycle = NewcomerLifecycle.SUBMITTED,
    @Unredacted val caregiverPublicId: String? = null,
    @Unredacted val identityStatus: NewcomerIdentityStatus? = null,
    val workOrSchool: String? = null,
    val firstVisitDate: LocalDate? = null,
    @Unredacted val assignedGroupPublicId: String? = null,
    val assignmentReason: String? = null,
    val overallNotes: String? = null,
    @Unredacted val postAssignmentAttendance: PostAssignmentAttendance? = null,
    val kakaoId: String? = null,
    val previousChurch: String? = null,
    @Unredacted val churchExperience: ChurchExperience? = null,
    val visitMotives: List<String> = emptyList(),
    val additionalNotes: String? = null,
)

@Redacted
data class UpdateNewcomerRequest(
    val lastName: String? = null,
    val firstName: String? = null,
    val englishName: String? = null,
    val gender: Gender? = null,
    val birthDate: LocalDate? = null,
    @field:Email val email: String? = null,
    @field:Size(max = 50) val phoneNumber: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val baptism: Baptism? = null,
    val profileImageUrl: String? = null,
    val registrationDate: LocalDate? = null,
    @field:Min(1) @field:Max(99) val intakeRound: Int? = null,
    @Unredacted val hasVisited: Boolean? = null,
    @Unredacted val lifecycleStatus: NewcomerLifecycle? = null,
    @Unredacted val caregiverPublicId: String? = null,
    @Unredacted val identityStatus: NewcomerIdentityStatus? = null,
    val workOrSchool: String? = null,
    val firstVisitDate: LocalDate? = null,
    @Unredacted val assignedGroupPublicId: String? = null,
    val assignmentReason: String? = null,
    val overallNotes: String? = null,
    @Unredacted val postAssignmentAttendance: PostAssignmentAttendance? = null,
    val kakaoId: String? = null,
    val previousChurch: String? = null,
    @Unredacted val churchExperience: ChurchExperience? = null,
    val visitMotives: List<String>? = null,
    val additionalNotes: String? = null,
    @Unredacted val version: Long,
)

@Redacted
data class NewcomerResponse(
    @Unredacted val publicId: String,
    @Unredacted val memberPublicId: String,
    val lastName: String,
    val firstName: String,
    val englishName: String? = null,
    val gender: Gender? = null,
    val birthDate: LocalDate? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val baptism: Baptism? = null,
    val profileImageUrl: String? = null,
    val registrationDate: LocalDate? = null,
    @Unredacted val intakeRound: Int? = null,
    @Unredacted val hasVisited: Boolean,
    @Unredacted val lifecycleStatus: NewcomerLifecycle,
    @Unredacted val caregiver: NewcomerOption? = null,
    @Unredacted val identityStatus: NewcomerIdentityStatus? = null,
    val workOrSchool: String? = null,
    val firstVisitDate: LocalDate? = null,
    @Unredacted val assignedGroup: NewcomerOption? = null,
    val assignmentReason: String? = null,
    val overallNotes: String? = null,
    @Unredacted val postAssignmentAttendance: PostAssignmentAttendance? = null,
    val kakaoId: String? = null,
    val previousChurch: String? = null,
    @Unredacted val churchExperience: ChurchExperience? = null,
    val visitMotives: List<String> = emptyList(),
    val additionalNotes: String? = null,
    @Unredacted val version: Long,
    @Unredacted val createdAt: Instant?,
    @Unredacted val updatedAt: Instant?,
)

data class NewcomerOption(
    val publicId: String,
    val label: String,
)

data class NewcomerOptionsResponse(
    val caregivers: List<NewcomerOption>,
    val groups: List<NewcomerOption>,
    val identityStatuses: List<NewcomerIdentityStatus> = NewcomerIdentityStatus.entries,
    val attendanceStatuses: List<PostAssignmentAttendance> = PostAssignmentAttendance.entries,
)
