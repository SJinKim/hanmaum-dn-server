package com.hanmaum.dn.app.features.newcomers.api

import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerOption
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerResponse
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile

private const val LIST_SEPARATOR = "\u001F"

fun NewcomerProfile.toResponse(): NewcomerResponse =
    NewcomerResponse(
        publicId = publicId.toString(),
        memberPublicId = member.publicId.toString(),
        lastName = member.lastName,
        firstName = member.firstName,
        englishName = englishName,
        gender = member.gender,
        birthDate = member.birthDate,
        email = member.email,
        phoneNumber = member.phoneNumber,
        street = member.street,
        houseNumber = member.houseNumber,
        zipCode = member.zipCode,
        city = member.city,
        baptism = member.baptism,
        profileImageUrl = member.profileImageUrl,
        registrationDate = member.registrationDate,
        intakeRound = intakeRound,
        hasVisited = hasVisited,
        lifecycleStatus = lifecycleStatus,
        caregiver = caregiver?.let { NewcomerOption(it.publicId.toString(), it.getFullName()) },
        identityStatus = identityStatus,
        workOrSchool = workOrSchool,
        firstVisitDate = firstVisitDate,
        assignedGroup = assignedGroup?.let { NewcomerOption(it.publicId.toString(), it.getFullName()) },
        assignmentReason = assignmentReason,
        overallNotes = overallNotes,
        postAssignmentAttendance = postAssignmentAttendance,
        kakaoId = kakaoId,
        previousChurch = previousChurch,
        churchExperience = churchExperience,
        visitMotives = visitMotives?.split(LIST_SEPARATOR).orEmpty(),
        additionalNotes = additionalNotes,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
