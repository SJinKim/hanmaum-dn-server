package com.hanmaum.dn.app.features.notifications.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

enum class DevicePlatform {
    ANDROID,
    IOS,
}

@Entity
@Table(name = "device_tokens")
class DeviceToken(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    var member: Member,
    @Column(nullable = false, unique = true, length = 512)
    var token: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var platform: DevicePlatform,
) : BaseEntity()
