package com.hanmaum.dn.app.common.observability

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

enum class ExternalCallOutcome(
    val tagValue: String,
) {
    SUCCESS("success"),
    TRANSPORT_ERROR("transport_error"),
    CLIENT_ERROR("client_error"),
    SERVER_ERROR("server_error"),
    INVALID_RESPONSE("invalid_response"),
    UNEXPECTED_ERROR("unexpected_error"),
}

enum class OperationOutcome(
    val tagValue: String,
) {
    SUCCESS("success"),
    FAILURE("failure"),
}

@Component
class OperationalMetrics(
    private val registry: MeterRegistry,
) {
    fun recordExternalCall(
        dependency: String,
        operation: String,
        outcome: ExternalCallOutcome,
        elapsedNanos: Long,
    ) {
        val tags =
            arrayOf(
                "dependency",
                dependency,
                "operation",
                operation,
                "outcome",
                outcome.tagValue,
            )
        registry.counter(EXTERNAL_CALLS, *tags).increment()
        registry.timer(EXTERNAL_CALL_DURATION, *tags).record(elapsedNanos, TimeUnit.NANOSECONDS)
    }

    fun recordFcmMessages(
        successCount: Int,
        failuresByReason: Map<String, Int>,
    ) {
        if (successCount > 0) {
            registry
                .counter(FCM_MESSAGES, "outcome", "success", "reason", "none")
                .increment(successCount.toDouble())
        }
        failuresByReason.forEach { (reason, count) ->
            if (count > 0) {
                registry
                    .counter(FCM_MESSAGES, "outcome", "failure", "reason", reason)
                    .increment(count.toDouble())
            }
        }
    }

    fun recordNotificationFanout(
        outcome: OperationOutcome,
        elapsedNanos: Long,
    ) {
        val tags = arrayOf("outcome", outcome.tagValue)
        registry.counter(NOTIFICATION_FANOUT, *tags).increment()
        registry.timer(NOTIFICATION_FANOUT_DURATION, *tags).record(elapsedNanos, TimeUnit.NANOSECONDS)
    }

    fun recordBackgroundJob(
        job: String,
        outcome: OperationOutcome,
        elapsedNanos: Long,
    ) {
        val tags = arrayOf("job_name", job, "outcome", outcome.tagValue)
        registry.counter(BACKGROUND_JOBS, *tags).increment()
        registry.timer(BACKGROUND_JOB_DURATION, *tags).record(elapsedNanos, TimeUnit.NANOSECONDS)
    }

    /**
     * One counter per request for the church geofence, tagged success or failure. Failure
     * means the deployment has no geofence configured and the endpoint answered 503, so
     * the failure rate is what to alert on — a geofence that goes dark silently disables
     * attendance check-in in the app.
     */
    fun recordChurchLocationRequest(outcome: OperationOutcome) {
        registry.counter(CHURCH_LOCATION_REQUESTS, "outcome", outcome.tagValue).increment()
    }

    private companion object {
        const val EXTERNAL_CALLS = "hanmaum.external.calls"
        const val EXTERNAL_CALL_DURATION = "hanmaum.external.call.duration"
        const val FCM_MESSAGES = "hanmaum.fcm.messages"
        const val NOTIFICATION_FANOUT = "hanmaum.notification.fanout"
        const val NOTIFICATION_FANOUT_DURATION = "hanmaum.notification.fanout.duration"
        const val BACKGROUND_JOBS = "hanmaum.background.jobs"
        const val BACKGROUND_JOB_DURATION = "hanmaum.background.job.duration"
        const val CHURCH_LOCATION_REQUESTS = "hanmaum.church.location.requests"
    }
}
