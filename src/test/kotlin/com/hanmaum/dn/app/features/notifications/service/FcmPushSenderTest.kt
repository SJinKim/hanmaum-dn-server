package com.hanmaum.dn.app.features.notifications.service

import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.SendResponse
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any

@ExtendWith(MockitoExtension::class)
class FcmPushSenderTest {
    @Mock private lateinit var messaging: FirebaseMessaging

    @Mock private lateinit var response: BatchResponse

    @Mock private lateinit var successfulResponse: SendResponse

    @Mock private lateinit var failedResponse: SendResponse

    @Mock private lateinit var firebaseException: FirebaseMessagingException

    private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    @Test
    fun `records partial FCM failures and returns dead tokens`() {
        `when`(successfulResponse.isSuccessful).thenReturn(true)
        `when`(failedResponse.isSuccessful).thenReturn(false)
        `when`(failedResponse.exception).thenReturn(firebaseException)
        `when`(firebaseException.messagingErrorCode).thenReturn(MessagingErrorCode.UNREGISTERED)
        `when`(response.responses).thenReturn(listOf(successfulResponse, failedResponse))
        `when`(messaging.sendEachForMulticast(any<MulticastMessage>())).thenReturn(response)
        val sender = FcmPushSender(messaging, OperationalMetrics(registry))

        val deadTokens = sender.send(listOf("active-token", "dead-token"), "title", "body", emptyMap(), null)

        assertEquals(listOf("dead-token"), deadTokens)
        assertEquals(
            1.0,
            registry
                .find("hanmaum.fcm.messages")
                .tags("outcome", "success", "reason", "none")
                .counter()
                ?.count(),
        )
        assertEquals(
            1.0,
            registry
                .find("hanmaum.fcm.messages")
                .tags("outcome", "failure", "reason", "unregistered")
                .counter()
                ?.count(),
        )
    }

    @Test
    fun `records every token as failed when FCM call throws`() {
        `when`(messaging.sendEachForMulticast(any<MulticastMessage>()))
            .thenThrow(IllegalStateException("firebase unavailable"))
        val sender = FcmPushSender(messaging, OperationalMetrics(registry))

        val deadTokens = sender.send(listOf("one", "two"), "title", "body", emptyMap(), null)

        assertTrue(deadTokens.isEmpty())
        assertEquals(
            2.0,
            registry
                .find("hanmaum.fcm.messages")
                .tags("outcome", "failure", "reason", "unexpected")
                .counter()
                ?.count(),
        )
    }
}
