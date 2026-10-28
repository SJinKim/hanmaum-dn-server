package com.hanmaum.dn.app.common.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse
import software.amazon.awssdk.services.s3.model.S3Error
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class S3StorageServiceTest {
    private val s3Client = mock<S3Client>()
    private val properties =
        S3StorageProperties(
            endpoint = "http://localhost:9000",
            region = "us-east-1",
            bucket = "bucket",
            accessKey = "key",
            secretKey = "secret",
            createBucket = false,
            getUrlTtl = Duration.ofDays(7),
        )
    private val service = S3StorageService(s3Client, mock<S3Presigner>(), properties)

    @Test
    fun `deleteObjects fails when the bucket reports an error for a key despite a 200`() {
        val denied =
            S3Error
                .builder()
                .key("profile-images/a.jpg")
                .code("AccessDenied")
                .build()
        whenever(s3Client.deleteObjects(any<DeleteObjectsRequest>()))
            .thenReturn(DeleteObjectsResponse.builder().errors(denied).build())

        val failure = assertThrows<ObjectDeletionFailedException> { service.deleteObjects(listOf("profile-images/a.jpg")) }

        assertEquals(listOf("profile-images/a.jpg: AccessDenied"), failure.failures)
        assertTrue(failure.message!!.contains("AccessDenied"))
    }

    @Test
    fun `deleteObjects succeeds when the bucket reports no errors`() {
        whenever(s3Client.deleteObjects(any<DeleteObjectsRequest>())).thenReturn(DeleteObjectsResponse.builder().build())

        service.deleteObjects(listOf("profile-images/a.jpg"))
    }
}
