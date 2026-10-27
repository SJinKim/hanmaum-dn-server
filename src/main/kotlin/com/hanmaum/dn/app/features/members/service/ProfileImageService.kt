package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import software.amazon.awssdk.core.exception.SdkException
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
) {
    private val logger = LoggerFactory.getLogger(ProfileImageService::class.java)

    /**
     * Replaces the caller's picture.
     *
     * The new object is written before the row changes, so a storage outage leaves the
     * member exactly as it was. The old object goes only after the commit: deleting it
     * earlier would leave the row pointing at nothing if the commit failed. The reverse
     * failure — a commit that fails after the upload — leaves an orphaned object, which
     * costs a few hundred kilobytes and no correctness.
     */
    @Transactional
    fun uploadForMember(
        keycloakSubject: String,
        bytes: ByteArray,
    ): Member {
        val member = currentMemberResolver.require(keycloakSubject)
        val image = processor.process(bytes)
        val key = "$KEY_PREFIX/${member.publicId}/${UUID.randomUUID()}.jpg"
        try {
            storage.putObject(key, image.bytes, image.contentType)
        } catch (e: SdkException) {
            throw ProfileImageStorageUnavailableException(e)
        }
        val previousKey = member.profileImageKey
        member.profileImageKey = key
        member.profileImageVersion += 1
        memberRepository.save(member)
        previousKey?.let { deleteAfterCommit(it) }
        return member
    }

    /** Removes the caller's own picture; a member without one is left untouched. */
    @Transactional
    fun deleteForMember(keycloakSubject: String) {
        clear(currentMemberResolver.require(keycloakSubject))
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

    /** Best-effort removal of an object whose row is already gone, e.g. during a purge. */
    fun deleteStoredObject(key: String) {
        try {
            storage.deleteObjects(listOf(key))
        } catch (e: SdkException) {
            logger.warn("Could not delete profile image object {}", key, e)
        }
    }

    private fun clear(member: Member) {
        val key = member.profileImageKey ?: return
        member.profileImageKey = null
        member.profileImageVersion += 1
        memberRepository.save(member)
        deleteAfterCommit(key)
    }

    private fun deleteAfterCommit(key: String) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteStoredObject(key)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = deleteStoredObject(key)
            },
        )
    }

    private companion object {
        const val KEY_PREFIX = "profile-images"
    }
}
