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
                putUrlTtl = Duration.ofMinutes(15),
            )
        val config = S3ClientConfig(props)
        service = S3StorageService(config.s3Client(), config.s3Presigner(), props)
        service.ensureBucket()
    }

    @Test
    fun `put get head delete round-trip`() {
        val key = "albums/test/hello.txt"
        assertFalse(service.objectExists(key))
        service.putObject(key, "hello".toByteArray(), "text/plain")
        assertTrue(service.objectExists(key))
        assertEquals("hello", String(service.getObjectBytes(key)))
        service.deleteObjects(listOf(key))
        assertFalse(service.objectExists(key))
    }

    @Test
    fun `presigned PUT uploads and presigned GET downloads`() {
        val key = "albums/test/presigned.txt"
        val putUrl = service.presignPut(key, "text/plain")
        val http = HttpClient.newHttpClient()
        val putResponse =
            http.send(
                HttpRequest
                    .newBuilder(URI(putUrl))
                    .header("Content-Type", "text/plain")
                    .PUT(HttpRequest.BodyPublishers.ofString("via-presign"))
                    .build(),
                HttpResponse.BodyHandlers.discarding(),
            )
        assertEquals(200, putResponse.statusCode())

        val getUrl = service.presignGet(key)
        val getResponse =
            http.send(
                HttpRequest.newBuilder(URI(getUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, getResponse.statusCode())
        assertEquals("via-presign", getResponse.body())
    }
}
