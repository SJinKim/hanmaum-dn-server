package com.hanmaum.dn.app.features.album.service

import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.album.domain.PhotoStatus
import com.hanmaum.dn.app.features.album.repository.AlbumPhotoRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Removes PENDING uploads whose client never confirmed (spec §3: >24 h). */
@Component
class PhotoCleanupJob(
    private val photoRepository: AlbumPhotoRepository,
    private val storage: S3StorageService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.storage.cleanup-cron:0 15 3 * * *}")
    @Transactional
    fun cleanupStalePendingPhotos() {
        val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)
        val stale = photoRepository.findByStatusAndCreatedAtBefore(PhotoStatus.PENDING, cutoff)
        if (stale.isEmpty()) return
        stale.forEach { photo ->
            photo.deletedAt = Instant.now()
            storage.deleteObjects(listOfNotNull(photo.originalKey, photo.thumbKey))
        }
        log.info("Photo cleanup: removed {} stale PENDING uploads", stale.size)
    }
}
