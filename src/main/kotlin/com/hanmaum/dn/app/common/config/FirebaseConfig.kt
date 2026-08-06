package com.hanmaum.dn.app.common.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import com.hanmaum.dn.app.features.notifications.service.FcmPushSender
import com.hanmaum.dn.app.features.notifications.service.PushSender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FirebaseConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun pushSender(
        @Value("\${FIREBASE_SERVICE_ACCOUNT_JSON:}") serviceAccountJson: String,
        operationalMetrics: OperationalMetrics,
    ): PushSender {
        if (serviceAccountJson.isBlank()) {
            log.warn("FIREBASE_SERVICE_ACCOUNT_JSON not set - push sending disabled (rows are still written)")
            return object : PushSender {
                override fun send(
                    tokens: List<String>,
                    title: String,
                    body: String,
                    data: Map<String, String>,
                    badge: Int?,
                ): List<String> {
                    operationalMetrics.recordFcmMessages(
                        successCount = 0,
                        failuresByReason = mapOf("disabled" to tokens.size),
                    )
                    return emptyList()
                }
            }
        }
        val app =
            if (FirebaseApp.getApps().isEmpty()) {
                val credentials =
                    GoogleCredentials.fromStream(
                        serviceAccountJson.byteInputStream(),
                    )
                val options =
                    FirebaseOptions
                        .builder()
                        .setCredentials(credentials)
                        .build()
                FirebaseApp.initializeApp(options)
            } else {
                FirebaseApp.getInstance()
            }
        return FcmPushSender(FirebaseMessaging.getInstance(app), operationalMetrics)
    }
}
