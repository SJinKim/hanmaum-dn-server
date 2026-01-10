package com.hanmaum.dn.app.features.members.api

import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.domain.Member

// Hilfsfunktion: String -> Enum
private fun mapGender(genderStr: String?): Gender? {
    return when (genderStr?.uppercase()) {
        "M", "남", "MALE" -> Gender.M
        "F", "여", "FEMALE" -> Gender.F
        else -> null
    }
}

// Hilfsfunktion für Status
private fun mapStatus(statusStr: String?): MemberStatus {
    // Falls null, nehmen wir an, es soll ACTIVE bleiben oder ignoriert werden?
    // Hier sagen wir: Wenn unbekannt, dann ACTIVE (oder wirf Fehler)
    return try {
        if (statusStr == null) MemberStatus.ACTIVE
        else MemberStatus.valueOf(statusStr.uppercase()) // "active" -> ACTIVE
    } catch (e: IllegalArgumentException) {
        MemberStatus.ACTIVE // Fallback, falls Quatsch gesendet wird
    }
}

fun CreateMemberRequest.toEntity(): Member {
    return Member(
        // Namen direkt übernehmen
        lastName = this.lastName,
        firstName = this.firstName,
        discriminator = this.discriminator,

        // Gender konvertieren
        gender = mapGender(this.gender),

        birthDate = this.birthDate,
        phoneNumber = this.phoneNumber,
        email = this.email,

        // Adresse (Strukturiert)
        street = this.street,
        zipCode = this.zipCode,
        city = this.city,

        registrationDate = this.registrationDate,
        role = this.role
    )
}

fun Member.updateForm(request: UpdateMemberRequest) {
    // Einfaches Update der Felder
    this.lastName = request.lastName
    this.firstName = request.firstName
    this.discriminator = request.discriminator

    this.gender = mapGender(request.gender)

    this.birthDate = request.birthDate
    this.phoneNumber = request.phoneNumber
    this.email = request.email

    // Adresse
    this.street = request.street
    this.zipCode = request.zipCode
    this.city = request.city

    this.memberStatus = mapStatus(request.memberStatus)
    this.role = request.role
}