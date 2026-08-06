package com.hanmaum.dn.app.features.notifications.service

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import org.slf4j.LoggerFactory

class FcmPushSender(
    private val messaging: FirebaseMessaging,
    private val operationalMetrics: OperationalMetrics,
) : PushSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>,
        badge: Int?,
    ): List<String> {
        if (tokens.isEmpty()) return emptyList()
        return try {
            val notification =
                Notification
                    .builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            val androidNotification =
                AndroidNotification
                    .builder()
                    .setChannelId("announcements")
                    .build()
            val androidConfig =
                AndroidConfig
                    .builder()
                    .setNotification(androidNotification)
                    .build()
            val apsBuilder =
                Aps
                    .builder()
                    .setSound("default")
            if (badge != null) {
                apsBuilder.setBadge(badge)
            }
            val aps = apsBuilder.build()
            val apnsConfig =
                ApnsConfig
                    .builder()
                    .setAps(aps)
                    .build()
            val message =
                MulticastMessage
                    .builder()
                    .addAllTokens(tokens)
                    .setNotification(notification)
                    .putAllData(data)
                    .setAndroidConfig(androidConfig)
                    .setApnsConfig(apnsConfig)
                    .build()
            val response = messaging.sendEachForMulticast(message)
            val successCount = response.responses.count { it.isSuccessful }
            val failuresByReason =
                response.responses
                    .filterNot { it.isSuccessful }
                    .groupingBy { responseItem ->
                        responseItem.exception
                            ?.messagingErrorCode
                            ?.name
                            ?.lowercase()
                            ?: "unknown"
                    }.eachCount()
            operationalMetrics.recordFcmMessages(successCount, failuresByReason)
            if (failuresByReason.isNotEmpty()) {
                log
                    .atWarn()
                    .addKeyValue("event.action", "fcm.multicast.send")
                    .addKeyValue("event.outcome", "partial_failure")
                    .addKeyValue("requested_count", tokens.size)
                    .addKeyValue("success_count", successCount)
                    .addKeyValue("failure_count", failuresByReason.values.sum())
                    .addKeyValue("failure_reasons", failuresByReason.toSortedMap())
                    .log("FCM multicast send partially failed")
            }
            response.responses
                .mapIndexedNotNull { i, r ->
                    val code = r.exception?.messagingErrorCode
                    if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                        tokens[i]
                    } else {
                        null
                    }
                }
        } catch (e: FirebaseMessagingException) {
            val reason = e.messagingErrorCode?.name?.lowercase() ?: "firebase_error"
            operationalMetrics.recordFcmMessages(0, mapOf(reason to tokens.size))
            log
                .atError()
                .setCause(e)
                .addKeyValue("event.action", "fcm.multicast.send")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("requested_count", tokens.size)
                .addKeyValue("error.type", reason)
                .log("FCM multicast send failed")
            emptyList()
        } catch (e: RuntimeException) {
            operationalMetrics.recordFcmMessages(0, mapOf("unexpected" to tokens.size))
            log
                .atError()
                .setCause(e)
                .addKeyValue("event.action", "fcm.multicast.send")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("requested_count", tokens.size)
                .addKeyValue("error.type", "unexpected")
                .log("FCM multicast send failed")
            emptyList()
        }
    }
}
