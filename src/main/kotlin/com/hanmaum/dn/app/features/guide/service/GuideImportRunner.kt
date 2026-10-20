package com.hanmaum.dn.app.features.guide.service

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
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
        environment: Environment,
        context: ConfigurableApplicationContext,
    ): ApplicationRunner =
        ApplicationRunner {
            var completed = false
            try {
                require(environment.acceptsProfiles(Profiles.of("guide-import"))) {
                    "Guide import requires the explicit guide-import maintenance profile."
                }
                execute(properties, service)
                completed = true
            } finally {
                // Failures abort startup immediately; success closes only after ALL startup runners.
                if (!completed) SpringApplication.exit(context)
            }
        }

    @Bean
    @ConditionalOnProperty(prefix = "app.guide-import", name = ["enabled"], havingValue = "true")
    fun guideImportShutdown(context: ConfigurableApplicationContext): ApplicationListener<ApplicationReadyEvent> =
        object : ApplicationListener<ApplicationReadyEvent>, Ordered {
            override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

            override fun onApplicationEvent(event: ApplicationReadyEvent) {
                if (event.applicationContext === context) SpringApplication.exit(context)
            }
        }

    private fun execute(
        properties: GuideImportProperties,
        service: GuideImportService,
    ) {
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
