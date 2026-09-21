package com.hanmaum.dn.app.features.newcomers.repository

import com.hanmaum.dn.app.features.newcomers.domain.NewcomerImportRecord
import org.springframework.data.jpa.repository.JpaRepository

interface NewcomerImportRecordRepository : JpaRepository<NewcomerImportRecord, Long> {
    fun existsBySourceFingerprintAndRowNumber(
        sourceFingerprint: String,
        rowNumber: Int,
    ): Boolean
}
