package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.storage.S3StorageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration

/**
 * Durable list of bucket objects that may be orphaned (HDN-140).
 *
 * A key is [track]ed in its own transaction before it is written, and [release]d once the member row
 * references it; a key that stops being referenced is tracked as well. Whatever is still listed after
 * [gracePeriod] is deleted by [sweep] unless a member references it, so every failure path — a rolled
 * back upload, a failed delete, a crash between the two — converges to an empty bucket-side orphan set.
 */
@Component
class ProfileImageCleanup(
    private val jdbcTemplate: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
    private val storage: S3StorageService,
    @Value("\${app.profile-image.cleanup-grace:PT15M}") private val gracePeriod: Duration,
) {
    private val logger = LoggerFactory.getLogger(ProfileImageCleanup::class.java)
    private val independent =
        TransactionTemplate(transactionManager).apply { propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW }

    /** Lists [key] as a candidate in a transaction of its own, so a rollback of the caller keeps it. */
    fun track(key: String) {
        independent.executeWithoutResult { insert(key) }
    }

    /** Lists [key] in the caller's transaction: it only counts if the caller commits. */
    fun trackInCurrentTransaction(key: String) = insert(key)

    /** Always in a transaction of its own: callers run it from transaction callbacks, where the outer one is spent. */
    fun release(key: String) {
        independent.executeWithoutResult { jdbcTemplate.update("DELETE FROM profile_image_cleanup WHERE object_key = ?", key) }
    }

    @Scheduled(fixedDelayString = "\${app.profile-image.cleanup-delay:PT10M}")
    fun sweep() {
        val candidates =
            jdbcTemplate.queryForList(
                "SELECT object_key FROM profile_image_cleanup WHERE created_at < now() - make_interval(secs => ?)",
                String::class.java,
                gracePeriod.seconds.toDouble(),
            )
        candidates.forEach { key ->
            try {
                if (!isReferenced(key)) storage.deleteObjects(listOf(key))
                release(key)
            } catch (e: RuntimeException) {
                logger.warn("Profile image cleanup failed, will retry key={}", key, e)
            }
        }
    }

    private fun insert(key: String) {
        jdbcTemplate.update("INSERT INTO profile_image_cleanup (object_key) VALUES (?) ON CONFLICT DO NOTHING", key)
    }

    private fun isReferenced(key: String): Boolean =
        jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM members WHERE profile_image_key = ?)", Boolean::class.java, key) == true
}
