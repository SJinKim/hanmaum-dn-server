package com.hanmaum.dn.app.common.storage

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * S3-compatible object storage settings. Endpoint is blank for real AWS S3;
 * set it (e.g. http://localhost:9000) for MinIO/dev — that also switches the
 * client to path-style access, which MinIO requires.
 */
@ConfigurationProperties(prefix = "app.storage.s3")
data class S3StorageProperties(
    val endpoint: String = "",
    val region: String = "eu-central-1",
    val bucket: String = "",
    val accessKey: String = "",
    val secretKey: String = "",
    val createBucket: Boolean = false,
    val getUrlTtl: Duration = Duration.ofDays(7),
)
