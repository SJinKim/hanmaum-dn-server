package com.hanmaum.dn.app.features.bulletin.repository

import com.hanmaum.dn.app.features.bulletin.domain.BulletinService
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BulletinServiceRepository : JpaRepository<BulletinService, Long> {
    fun findAllByDeletedAtIsNullOrderBySortOrderAsc(): List<BulletinService>

    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): BulletinService?

    fun findByIsBulletinDefaultTrueAndDeletedAtIsNull(): BulletinService?
}
