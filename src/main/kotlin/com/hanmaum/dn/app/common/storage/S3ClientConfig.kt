package com.hanmaum.dn.app.common.storage

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@Configuration
@EnableConfigurationProperties(S3StorageProperties::class)
class S3ClientConfig(
    private val properties: S3StorageProperties,
) {
    // Resolved per request, not at startup: AwsBasicCredentials rejects blank keys, and the
    // app (and every test context) must boot without S3 configured.
    private val credentials = AwsCredentialsProvider { AwsBasicCredentials.create(properties.accessKey, properties.secretKey) }

    @Bean
    fun s3Client(): S3Client {
        val builder =
            S3Client
                .builder()
                .region(Region.of(properties.region))
                .credentialsProvider(credentials)
        if (properties.endpoint.isNotBlank()) {
            builder
                .endpointOverride(URI.create(properties.endpoint))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        }
        return builder.build()
    }

    @Bean
    fun s3Presigner(): S3Presigner {
        val builder =
            S3Presigner
                .builder()
                .region(Region.of(properties.region))
                .credentialsProvider(credentials)
        val endpoint = properties.publicEndpoint.ifBlank { properties.endpoint }
        if (endpoint.isNotBlank()) {
            builder
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        }
        return builder.build()
    }
}
