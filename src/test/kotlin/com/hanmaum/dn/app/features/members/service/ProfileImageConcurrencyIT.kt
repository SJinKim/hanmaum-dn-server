package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Two uploads of the same member's picture (HDN-140). Without the row lock both read the same
 * previous key, the later commit wins, and the object the earlier one wrote is never deleted.
 * With it the second upload waits, sees the first one's key and removes it after commit.
 */
@DataJpaTest(properties = ["spring.jpa.show-sql=false"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Import(
    PiiCryptoConfiguration::class,
    CurrentMemberResolver::class,
    ProfileImageService::class,
    ProfileImageProcessor::class,
    ProfileImageCleanup::class,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProfileImageConcurrencyIT {
    @Autowired private lateinit var service: ProfileImageService

    @Autowired private lateinit var members: MemberRepository

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @MockitoBean private lateinit var storage: S3StorageService

    @Test
    fun `a second upload waits for the first and deletes its object`() {
        val subject = "profile-image-lock"
        val memberId = members.saveAndFlush(Member(lastName = "Test", firstName = "Member").apply { keycloakId = subject }).id!!
        val tx = TransactionTemplate(transactionManager)
        val written = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first =
                pool.submit<String> {
                    tx.execute {
                        val key = service.uploadForMember(subject, ByteArrayInputStream(png())).profileImageKey!!
                        written.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        key
                    }
                }
            assertTrue(written.await(10, TimeUnit.SECONDS))

            val second =
                pool.submit<String> {
                    secondStarted.countDown()
                    service.uploadForMember(subject, ByteArrayInputStream(png())).profileImageKey!!
                }
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
            assertFailsWith<TimeoutException> { second.get(200, TimeUnit.MILLISECONDS) }

            release.countDown()
            val firstKey = first.get(10, TimeUnit.SECONDS)
            val secondKey = second.get(10, TimeUnit.SECONDS)

            assertNotEquals(firstKey, secondKey)
            val stored = members.findById(memberId).orElseThrow()
            assertEquals(secondKey, stored.profileImageKey)
            assertEquals(2, stored.profileImageVersion)
            verify(storage).deleteObjects(listOf(firstKey))
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
            tx.executeWithoutResult {
                members.deleteById(memberId)
                members.flush()
            }
        }
    }

    private fun png(): ByteArray =
        ByteArrayOutputStream().use { out ->
            ImageIO.write(BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "png", out)
            out.toByteArray()
        }
}
