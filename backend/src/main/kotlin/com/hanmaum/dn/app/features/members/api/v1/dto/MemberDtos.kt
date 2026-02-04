package com.hanmaum.dn.app.features.members.api.v1.dto

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

data class MemberDto(
    val id: String, // fachliche ID

    val lastName: String,
    val firstName: String,
    val discriminator: String? = null, // Zur Unterscheidung bei gleichen Namen (A, B, C)

    val gender: String? = null,        // "M" oder "F"
    val baptism: String? = null,       // "INFANT_BAPTIZED", "CONFIRMATION", etc.

    val birthDate: LocalDate? = null,
    val phoneNumber: String? = null,
    val email: String? = null,

    // Adresse
    val street: String? = null,
    val zipCode: String? = null,
    val city: String? = null,

    val registrationDate: LocalDate? = null,
    val memberStatus: String,          // "ACTIVE", "DELETED" etc.

    val role: String? = null,          // z.B. "Teacher", "Student"
    val groupName: String? = null      // Name der Gruppe (z.B. "Sarang")
)

data class CreateMemberRequest(
    // 1. Namen (Getrennt, wie in DB)
    val lastName: String,
    val firstName: String,
    val discriminator: String? = null,

    // 2. Stammdaten
    val gender: String? = null, // "M", "F" (Frontend schickt String)
    val baptism: String? = null,

    val birthDate: LocalDate? = null,
    val phoneNumber: String? = null,
    val email: String? = null,

    // 3. Adresse (Jetzt 3 Felder statt addressStreet)
    val street: String? = null,
    val zipCode: String? = null,
    val city: String? = null,

    val registrationDate: LocalDate? = null,
    val role: String? = null,

    // Optional: Falls du beim Erstellen schon eine Gruppe zuweisen willst
    val groupId: Long? = null
)

data class UpdateMemberRequest(
    val lastName: String,
    val firstName: String,
    val discriminator: String? = null,

    val gender: String? = null,
    val baptism: String? = null,

    val birthDate: LocalDate? = null,
    val phoneNumber: String? = null,
    val email: String? = null,

    val street: String? = null,
    val zipCode: String? = null,
    val city: String? = null,

    val registrationDate: LocalDate? = null,
    val memberStatus: String, // z.B. "ACTIVE", "INACTIVE"

    val role: String? = null,
    val groupId: Long? = null,
)

data class RegisterMemberRequest(
    // PFLICHTFELDER
    @field:NotBlank(message = "Vorname ist Pflicht")
    val firstName: String,

    @field:NotBlank(message = "Nachname ist Pflicht")
    val lastName: String,

    @field:NotBlank
    val password: String,

    @field:NotBlank(message = "Muss eine gültige E-Mail sein")
    val email: String,

    @field:NotBlank(message = "Stadt ist Pflicht")
    val city: String,

    // OPTIONAL
    val baptism: String? = null, // Enum String
    val gender: String? = null,
    val birthDate: LocalDate? = null, // Nutze LocalDate im Backend!
    val phoneNumber: String? = null,
    val street: String? = null,
    val zipCode: String? = null
)

data class MemberResponse(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val email: String?,

    // READ ONLY Felder für den User
    val role: String,       // "MEMBER"
    val groupName: String?, // "Jugendgruppe" statt ID 3

    val city: String,
    val status: MemberStatus,
)