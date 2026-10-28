package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.core.exception.SdkClientException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The sweeper drains the cleanup list without ever deleting an object a member still shows (HDN-140). */
@DataJpaTest(properties = ["spring.jpa.show-sql=false"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Import(PiiCryptoConfiguration::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProfileImageCleanupIT {
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @Autowired private lateinit var members: MemberRepository

    private val storage = mock<S3StorageService>()

    private fun cleanup() = ProfileImageCleanup(jdbcTemplate, transactionManager, storage, Duration.ZERO)

    private fun pending(): List<String> = jdbcTemplate.queryForList("SELECT object_key FROM profile_image_cleanup", String::class.java)

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM profile_image_cleanup")
        members.deleteAll()
    }

    @Test
    fun `sweep deletes an unreferenced object and drops its row`() {
        val cleanup = cleanup()
        cleanup.track("profile-images/orphan.jpg")

        cleanup.sweep()

        verify(storage).deleteObjects(listOf("profile-images/orphan.jpg"))
        assertEquals(emptyList(), pending())
    }

    @Test
    fun `sweep keeps the object a member still references`() {
        members.saveAndFlush(Member(lastName = "Kim", firstName = "Min").apply { profileImageKey = "profile-images/live.jpg" })
        val cleanup = cleanup()
        cleanup.track("profile-images/live.jpg")

        cleanup.sweep()

        verify(storage, never()).deleteObjects(any())
        assertEquals(emptyList(), pending())
    }

    @Test
    fun `sweep keeps a row that was listed again after it read it`() {
        val cleanup = cleanup()
        cleanup.track("profile-images/relisted.jpg")
        // A producer needs the retry again while the sweeper is talking to the bucket.
        doAnswer { cleanup.track("profile-images/relisted.jpg") }.`when`(storage).deleteObjects(any())

        cleanup.sweep()

        assertEquals(listOf("profile-images/relisted.jpg"), pending())
    }

    @Test
    fun `releaseIfUnchanged keeps a row that a delete listed again after the upload listed it`() {
        val cleanup = cleanup()
        val uploadListedAt = cleanup.track("profile-images/live.jpg")
        // A parallel delete re-lists the key before the upload's commit callback runs.
        cleanup.track("profile-images/live.jpg")

        cleanup.releaseIfUnchanged("profile-images/live.jpg", uploadListedAt)

        assertEquals(listOf("profile-images/live.jpg"), pending())
    }

    @Test
    fun `releaseIfUnchanged drops a row nobody listed again`() {
        val cleanup = cleanup()
        val listedAt = cleanup.track("profile-images/live.jpg")

        cleanup.releaseIfUnchanged("profile-images/live.jpg", listedAt)

        assertEquals(emptyList<String>(), pending())
    }

    @Test
    fun `listing a key twice keeps one row and restarts its grace period`() {
        val cleanup = cleanup()
        cleanup.track("profile-images/twice.jpg")
        val first = jdbcTemplate.queryForObject("SELECT created_at FROM profile_image_cleanup", java.sql.Timestamp::class.java)!!

        cleanup.track("profile-images/twice.jpg")

        val second = jdbcTemplate.queryForObject("SELECT created_at FROM profile_image_cleanup", java.sql.Timestamp::class.java)!!
        assertTrue(second.after(first), "expected created_at to move forward, was $first then $second")
    }

    @Test
    fun `sweep keeps the row when the bucket is down so the next run retries`() {
        doThrow(SdkClientException.create("down")).`when`(storage).deleteObjects(any())
        val cleanup = cleanup()
        cleanup.track("profile-images/orphan.jpg")

        cleanup.sweep()

        assertEquals(listOf("profile-images/orphan.jpg"), pending())
    }
}
