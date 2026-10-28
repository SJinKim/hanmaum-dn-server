package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.storage.ObjectDeletionFailedException
import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import software.amazon.awssdk.core.exception.SdkException
import java.io.InputStream
import java.util.UUID

/**
 * Owns a member's profile picture: the object in the private bucket and the key and
 * version on the member row (HDN-140).
 *
 * The bucket is private, so the stored key never leaves the server. Readers get a
 * presigned URL from [urlFor], and [Member.profileImageVersion] changes on every upload
 * and delete so clients can key their image cache on it instead of on the URL, whose
 * signature changes on every read.
 */
@Service
class ProfileImageService(
    private val memberRepository: MemberRepository,
    private val currentMemberResolver: CurrentMemberResolver,
    private val processor: ProfileImageProcessor,
    private val storage: S3StorageService,
    private val cleanup: ProfileImageCleanup,
) {
    private val logger = LoggerFactory.getLogger(ProfileImageService::class.java)

    /**
     * Replaces the caller's picture.
     *
     * The new object is written before the row changes, so a storage outage leaves the
     * member exactly as it was. The old object goes only after the commit: deleting it
     * earlier would leave the row pointing at nothing if the commit failed.
     *
     * Neither failure orphans an object for good: the new key is tracked in [ProfileImageCleanup]
     * before it is written and released after the commit, and a rollback removes it right away; the
     * old key is tracked in the same transaction, so a failed delete is retried by the sweeper.
     */
    @Transactional
    fun uploadForMember(
        keycloakSubject: String,
        upload: InputStream,
    ): Member {
        val member = currentMemberResolver.requireForUpdate(keycloakSubject)
        val image = processor.process(upload)
        val key = "$KEY_PREFIX/${member.publicId}/${UUID.randomUUID()}.jpg"
        cleanup.track(key)
        deleteOnRollback(key)
        try {
            storage.putObject(key, image.bytes, image.contentType)
        } catch (e: SdkException) {
            throw ProfileImageStorageUnavailableException(e)
        }
        val previousKey = member.profileImageKey
        member.profileImageKey = key
        member.profileImageVersion += 1
        memberRepository.save(member)
        releaseAfterCommit(key)
        previousKey?.let { deleteAfterCommit(it) }
        return member
    }

    /** Removes the caller's own picture; a member without one is left untouched. */
    @Transactional
    fun deleteForMember(keycloakSubject: String) {
        clear(currentMemberResolver.requireForUpdate(keycloakSubject))
    }

    /** Removes a member's picture for moderation; the member row is locked like every other admin write. */
    @Transactional
    fun deleteByAdmin(publicId: UUID) {
        val member =
            memberRepository
                .findForUpdateByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow { EntityNotFoundException("Member not found: $publicId") }
        clear(member)
    }

    /**
     * A short-lived URL for the member's picture, or null without one.
     *
     * A signing failure is logged and read as "no picture": a profile that loads without
     * its avatar is better than a profile that does not load.
     */
    fun urlFor(member: Member): String? {
        val key = member.profileImageKey ?: return null
        return try {
            storage.presignGet(key)
        } catch (e: SdkException) {
            logger.warn("Could not presign profile image for member {}", member.publicId, e)
            null
        }
    }

    /**
     * Removes an object nothing references any more, e.g. after a purge. A failure is not lost: the key
     * stays in [ProfileImageCleanup] and the sweeper retries it.
     */
    fun deleteStoredObject(key: String) {
        try {
            storage.deleteObjects(listOf(key))
            cleanup.release(key)
        } catch (e: SdkException) {
            retryLater(key, e)
        } catch (e: ObjectDeletionFailedException) {
            retryLater(key, e)
        }
    }

    private fun retryLater(
        key: String,
        cause: RuntimeException,
    ) {
        logger.warn("Could not delete profile image object, queued for retry key={}", key, cause)
        try {
            cleanup.track(key)
        } catch (e: DataAccessException) {
            logger.error("Could not queue profile image object for retry key={}", key, e)
        }
    }

    private fun clear(member: Member) {
        val key = member.profileImageKey ?: return
        member.profileImageKey = null
        member.profileImageVersion += 1
        memberRepository.save(member)
        deleteAfterCommit(key)
    }

    private fun releaseAfterCommit(key: String) {
        afterCompletion { status -> if (status == TransactionSynchronization.STATUS_COMMITTED) cleanup.release(key) }
    }

    private fun deleteOnRollback(key: String) {
        afterCompletion { status -> if (status != TransactionSynchronization.STATUS_COMMITTED) deleteStoredObject(key) }
    }

    /** The key stops being referenced with the caller's commit, so it is queued in the same transaction. */
    private fun deleteAfterCommit(key: String) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteStoredObject(key)
            return
        }
        cleanup.trackInCurrentTransaction(key)
        afterCompletion { status -> if (status == TransactionSynchronization.STATUS_COMMITTED) deleteStoredObject(key) }
    }

    private fun afterCompletion(action: (Int) -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCompletion(status: Int) = action(status)
            },
        )
    }

    private companion object {
        const val KEY_PREFIX = "profile-images"
    }
}
