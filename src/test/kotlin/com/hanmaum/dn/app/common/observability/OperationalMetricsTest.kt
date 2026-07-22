package com.hanmaum.dn.app.common.observability

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OperationalMetricsTest {
    private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val metrics = OperationalMetrics(registry)

    @Test
    fun `exports bounded operational metrics for alerting`() {
        metrics.recordExternalCall("keycloak", "create_user", ExternalCallOutcome.CLIENT_ERROR, 1_000)
        metrics.recordFcmMessages(2, mapOf("unregistered" to 1))
        metrics.recordNotificationFanout(OperationOutcome.FAILURE, 2_000)
        metrics.recordBackgroundJob("cleanup", OperationOutcome.SUCCESS, 3_000)

        assertEquals(
            1.0,
            registry
                .find("hanmaum.external.calls")
                .tags("dependency", "keycloak", "operation", "create_user", "outcome", "client_error")
                .counter()
                ?.count(),
        )
        assertEquals(
            2.0,
            registry
                .find("hanmaum.fcm.messages")
                .tags("outcome", "success", "reason", "none")
                .counter()
                ?.count(),
        )
        assertEquals(
            1.0,
            registry
                .find("hanmaum.background.jobs")
                .tags("job_name", "cleanup", "outcome", "success")
                .counter()
                ?.count(),
        )

        val scrape = registry.scrape()
        assertTrue(scrape.contains("hanmaum_external_calls_total"))
        assertTrue(scrape.contains("hanmaum_fcm_messages_total"))
        assertTrue(scrape.contains("hanmaum_notification_fanout_total"))
        assertTrue(scrape.contains("hanmaum_background_jobs_total"))
    }
}
