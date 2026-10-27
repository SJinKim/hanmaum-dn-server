package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.storage.S3ClientConfig
import com.hanmaum.dn.app.common.storage.S3StorageProperties
import com.hanmaum.dn.app.common.storage.S3StorageService
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Duration
import javax.imageio.ImageIO

/**
 * The real processor and a real bucket behind [ProfileImageService] (HDN-140): what lands in
 * storage is a 512×512 JPEG under the member's prefix, and a replaced picture is gone.
 *
 * Without a surrounding transaction the old object is deleted at once, which is the same
 * path the afterCommit hook runs.
 */
@Testcontainers
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfileImageServiceMinioIT {
    companion object {
        @Container
        @JvmStatic
        val minio: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2024-12-18T13-15-44Z")
    }

    private lateinit var storage: S3StorageService

    private val member = Member(lastName = "Kim", firstName = "Min")

    @BeforeAll
    fun setUp() {
        val props =
            S3StorageProperties(
                endpoint = minio.s3URL,
                region = "us-east-1",
                bucket = "hanmaum-dn-profile-test",
                accessKey = minio.userName,
                secretKey = minio.password,
                createBucket = true,
                getUrlTtl = Duration.ofDays(7),
            )
        val config = S3ClientConfig(props)
        storage = S3StorageService(config.s3Client(), config.s3Presigner(), props)
        storage.ensureBucket()
    }

    private fun service(): ProfileImageService {
        val repository = mock<MemberRepository> { on { save(any<Member>()) } doAnswer { it.getArgument(0) } }
        val resolver = mock<CurrentMemberResolver> { on { require("kc-1") } doReturn member }
        return ProfileImageService(repository, resolver, ProfileImageProcessor(), storage)
    }

    private fun png(
        width: Int,
        height: Int,
    ): ByteArray =
        ByteArrayOutputStream().use { out ->
            ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out)
            out.toByteArray()
        }

    @Test
    fun `upload stores a 512 JPEG and a second upload removes the first object`() {
        val service = service()

        service.uploadForMember("kc-1", png(1200, 800))
        val firstKey = member.profileImageKey!!
        assertTrue(firstKey.startsWith("profile-images/${member.publicId}/"))
        val stored = ImageIO.read(ByteArrayInputStream(storage.getObjectBytes(firstKey)))
        assertEquals(512, stored.width)
        assertEquals(512, stored.height)
        assertEquals(1, member.profileImageVersion)

        service.uploadForMember("kc-1", png(600, 900))
        val secondKey = member.profileImageKey!!
        assertNotEquals(firstKey, secondKey)
        assertTrue(storage.objectExists(secondKey))
        assertFalse(storage.objectExists(firstKey))
        assertEquals(2, member.profileImageVersion)
    }
}
