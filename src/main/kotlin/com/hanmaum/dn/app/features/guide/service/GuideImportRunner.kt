package com.hanmaum.dn.app.features.guide.service

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path
import java.util.UUID

@ConfigurationProperties("app.guide-import")
data class GuideImportProperties(
    val enabled: Boolean = false,
    val file: Path? = null,
    val dryRun: Boolean = true,
    val expectedSha256: String? = null,
    val rollbackBatch: UUID? = null,
)

@Configuration
@EnableConfigurationProperties(GuideImportProperties::class)
class GuideImportRunner {
    @Bean
    @ConditionalOnProperty(prefix = "app.guide-import", name = ["enabled"], havingValue = "true")
    fun guideImport(
        properties: GuideImportProperties,
        service: GuideImportService,
    ): ApplicationRunner =
        ApplicationRunner {
            val logger = LoggerFactory.getLogger(GuideImportRunner::class.java)
            if (properties.rollbackBatch != null) {
                require(properties.file == null) { "Choose import or rollback, not both." }
                val rows = service.rollback(properties.rollbackBatch, properties.dryRun)
                logger.info("Guide rollback dryRun={} batch={} reports={}", properties.dryRun, properties.rollbackBatch, rows)
            } else {
                val file = requireNotNull(properties.file) { "app.guide-import.file is required." }
                val result = service.import(file, properties.dryRun, properties.expectedSha256)
                logger.info(
                    "Guide import dryRun={} sourceSha256={} read={} recorded={} notes={} serviceSums={} new={} skipped={} conflicts={} warnings={} batch={}",
                    result.dryRun,
                    result.sourceSha256,
                    result.read,
                    result.recorded,
                    result.notes,
                    result.serviceSums,
                    result.newReports,
                    result.skipped,
                    result.conflictRows,
                    result.warnings,
                    result.batchPublicId,
                )
            }
        }
}
