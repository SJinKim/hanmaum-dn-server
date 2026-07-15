package com.hanmaum.dn.app.features.notifications.service

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory

class FcmPushSender(
    private val messaging: FirebaseMessaging,
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
            return response.responses
                .mapIndexedNotNull { i, r ->
                    val code = r.exception?.messagingErrorCode
                    if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                        tokens[i]
                    } else {
                        null
                    }
                }
        } catch (e: Exception) {
            log.warn("FCM multicast send failed for {} tokens", tokens.size, e)
            emptyList()
        }
    }
}
