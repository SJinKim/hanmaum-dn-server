package com.hanmaum.dn.app.features.members.api

import com.hanmaum.dn.app.features.members.api.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.domain.Member

fun CreateMemberRequest.toEntity(): Member {
    return Member(
        koreanName = this.koreanName,
        discriminator = this.discriminator,
        gender = this.gender,
        birthDate = this.birthDate,
        phoneNumber = this.phoneNumber,
        addressStreet = this.addressStreet,
        registrationDate = this.registrationDate,
    )
}

fun Member.updateForm(request: UpdateMemberRequest) {
    this.koreanName = request.koreanName
    this.discriminator = request.discriminator
    this.gender = request.gender
    this.birthDate = request.birthDate
    this.phoneNumber = request.phoneNumber
    this.addressStreet = request.addressStreet
    this.memberStatus = request.memberStatus
}