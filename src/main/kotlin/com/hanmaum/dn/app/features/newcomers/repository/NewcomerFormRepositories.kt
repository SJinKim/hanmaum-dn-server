package com.hanmaum.dn.app.features.newcomers.repository

import com.hanmaum.dn.app.features.newcomers.domain.NewcomerFormLink
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerFormSubmission
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface NewcomerFormLinkRepository : JpaRepository<NewcomerFormLink, Long> {
    fun findAllByDeletedAtIsNullOrderByCreatedAtDesc(): List<NewcomerFormLink>

    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): NewcomerFormLink?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM NewcomerFormLink l WHERE l.tokenHash = :tokenHash AND l.deletedAt IS NULL")
    fun findByTokenHashForUpdate(
        @Param("tokenHash") tokenHash: String,
    ): NewcomerFormLink?

    fun findByTokenHashAndDeletedAtIsNull(tokenHash: String): NewcomerFormLink?
}

interface NewcomerFormSubmissionRepository : JpaRepository<NewcomerFormSubmission, Long> {
    fun findByFormLinkIdAndIdempotencyHash(
        formLinkId: Long,
        idempotencyHash: String,
    ): NewcomerFormSubmission?
}
