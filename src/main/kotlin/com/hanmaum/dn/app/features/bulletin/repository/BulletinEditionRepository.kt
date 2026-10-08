package com.hanmaum.dn.app.features.bulletin.repository

import com.hanmaum.dn.app.features.bulletin.domain.BulletinEdition
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface BulletinEditionRepository : JpaRepository<BulletinEdition, Long> {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): BulletinEdition?

    fun findByServiceDateAndDeletedAtIsNull(serviceDate: LocalDate): BulletinEdition?
}
