package com.hanmaum.dn.app.features.newcomers.service

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(NewcomerImportProperties::class)
class NewcomerImportRunner {
    @Bean
    @ConditionalOnProperty(prefix = "app.newcomer-import", name = ["enabled"], havingValue = "true")
    fun newcomerImportApplicationRunner(
        properties: NewcomerImportProperties,
        service: NewcomerImportService,
    ): ApplicationRunner =
        ApplicationRunner {
            val file = requireNotNull(properties.file) { "app.newcomer-import.file is required when the importer is enabled." }
            val report = service.import(file, properties.layout, properties.dryRun, properties.decisions, properties.caregivers)
            logger.info(
                "Newcomer import completed dryRun={} read={} imported={} skipped={} review={} invalid={}",
                properties.dryRun,
                report.read,
                report.imported,
                report.skipped,
                report.review,
                report.invalid,
            )
            report.issues.forEach { issue ->
                logger.warn("Newcomer import issue rowNumber={} code={}", issue.rowNumber, issue.code)
            }
        }

    private companion object {
        val logger = LoggerFactory.getLogger(NewcomerImportRunner::class.java)
    }
}
