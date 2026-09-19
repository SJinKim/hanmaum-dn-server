package com.hanmaum.dn.app.features.newcomers.repository

import com.hanmaum.dn.app.features.newcomers.domain.NewcomerGraduation
import org.springframework.data.jpa.repository.JpaRepository

interface NewcomerGraduationRepository : JpaRepository<NewcomerGraduation, Long> {
    fun findByNewcomerProfileIdAndDeletedAtIsNull(newcomerProfileId: Long): NewcomerGraduation?
}
