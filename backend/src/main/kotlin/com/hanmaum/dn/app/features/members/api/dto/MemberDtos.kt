package com.hanmaum.dn.app.features.members.api.dto

import java.time.LocalDate

data class CreateMemberRequest(
    val koreanName: String,
    val discriminator: String? = null,
    val gender: String? = null,
    val birthDate: LocalDate? = null,
    val phoneNumber: String? = null,
    val addressStreet: String? = null,
    val registrationDate: LocalDate? = null,
)

data class UpdateMemberRequest(
    val koreanName: String,
    val discriminator: String? = null,
    val gender: String? = null,
    val birthDate: LocalDate? = null,
    val phoneNumber: String? = null,
    val addressStreet: String? = null,
    val memberStatus: String
)