package com.hanmaum.dn.app.common.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Testcontainers
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3StorageServiceIT {
    companion object {
        @Container
        @JvmStatic
        val minio: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2024-12-18T13-15-44Z")
    }

    private lateinit var service: S3StorageService

    @BeforeAll
    fun setUp() {
        val props =
            S3StorageProperties(
                endpoint = minio.s3URL,
                region = "us-east-1",
                bucket = "hanmaum-dn-test",
                accessKey = minio.userName,
                secretKey = minio.password,
                createBucket = true,
                getUrlTtl = Duration.ofDays(7),
            )
        val config = S3ClientConfig(props)
        service = S3StorageService(config.s3Client(), config.s3Presigner(), props)
        service.ensureBucket()
    }

    @Test
    fun `put get head delete round-trip`() {
        val key = "storage-test/hello.txt"
        assertFalse(service.objectExists(key))
        service.putObject(key, "hello".toByteArray(), "text/plain")
        assertTrue(service.objectExists(key))
        assertEquals("hello", String(service.getObjectBytes(key)))
        service.deleteObjects(listOf(key))
        assertFalse(service.objectExists(key))
    }

    @Test
    fun `presigned GET downloads the object`() {
        val key = "storage-test/presigned.txt"
        service.putObject(key, "via-presign".toByteArray(), "text/plain")

        val response =
            HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI(service.presignGet(key))).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, response.statusCode())
        assertEquals("via-presign", response.body())
    }
}
