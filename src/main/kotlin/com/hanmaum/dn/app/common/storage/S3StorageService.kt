package com.hanmaum.dn.app.common.storage

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

@Service
class S3StorageService(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: S3StorageProperties,
) {
    /** Dev/MinIO convenience only — prod buckets are provisioned out of band. */
    @PostConstruct
    fun ensureBucket() {
        if (!properties.createBucket) return
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket).build())
        } catch (e: NoSuchBucketException) {
            s3Client.createBucket { it.bucket(properties.bucket) }
        }
    }

    fun objectExists(key: String): Boolean =
        try {
            s3Client.headObject(
                HeadObjectRequest
                    .builder()
                    .bucket(properties.bucket)
                    .key(key)
                    .build(),
            )
            true
        } catch (e: NoSuchKeyException) {
            false
        }

    fun getObjectBytes(key: String): ByteArray =
        s3Client
            .getObjectAsBytes(
                GetObjectRequest
                    .builder()
                    .bucket(properties.bucket)
                    .key(key)
                    .build(),
            ).asByteArray()

    fun putObject(
        key: String,
        bytes: ByteArray,
        contentType: String,
    ) {
        s3Client.putObject(
            PutObjectRequest
                .builder()
                .bucket(properties.bucket)
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(bytes),
        )
    }

    fun deleteObjects(keys: List<String>) {
        if (keys.isEmpty()) return
        s3Client.deleteObjects(
            DeleteObjectsRequest
                .builder()
                .bucket(properties.bucket)
                .delete(
                    Delete
                        .builder()
                        .objects(keys.map { ObjectIdentifier.builder().key(it).build() })
                        .build(),
                ).build(),
        )
    }

    fun presignGet(key: String): String =
        s3Presigner
            .presignGetObject(
                GetObjectPresignRequest
                    .builder()
                    .signatureDuration(properties.getUrlTtl)
                    .getObjectRequest(
                        GetObjectRequest
                            .builder()
                            .bucket(properties.bucket)
                            .key(key)
                            .build(),
                    ).build(),
            ).url()
            .toString()

    fun presignPut(
        key: String,
        contentType: String,
    ): String =
        s3Presigner
            .presignPutObject(
                PutObjectPresignRequest
                    .builder()
                    .signatureDuration(properties.putUrlTtl)
                    .putObjectRequest(
                        PutObjectRequest
                            .builder()
                            .bucket(properties.bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    ).build(),
            ).url()
            .toString()
}
