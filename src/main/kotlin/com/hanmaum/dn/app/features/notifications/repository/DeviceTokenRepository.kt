package com.hanmaum.dn.app.features.notifications.repository

import com.hanmaum.dn.app.features.notifications.domain.DeviceToken
import org.springframework.data.jpa.repository.JpaRepository

interface DeviceTokenRepository : JpaRepository<DeviceToken, Long> {
    fun findByToken(token: String): DeviceToken?

    fun findAllByMemberIdIn(memberIds: Collection<Long>): List<DeviceToken>

    fun deleteAllByTokenIn(tokens: Collection<String>)

    fun deleteByTokenAndMemberId(
        token: String,
        memberId: Long,
    )
}
