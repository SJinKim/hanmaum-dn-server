package com.hanmaum.dn.app.features.members.api.v1.dto

import java.time.LocalDate

data class CreateMemberRequest(
    // 1. Namen (Getrennt, wie in DB)
    val lastName: String,
    val firstName: String,

    val discriminator: String? = null,

    // 2. Stammdaten
    val gender: String? = null, // "M", "F" (Frontend schickt String)
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
    val birthDate: LocalDate? = null,
    val phoneNumber: String? = null,
    val email: String? = null,

    val street: String? = null,
    val zipCode: String? = null,
    val city: String? = null,

    val memberStatus: String, // z.B. "ACTIVE", "INACTIVE"
    val role: String? = null,
    val groupId: Long? = null
)