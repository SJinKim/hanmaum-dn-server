package com.hanmaum.dn.app.features.notifications.service

interface PushSender {
    /**
     * Sends one push to every token. Returns the subset of tokens that FCM
     * reported as permanently invalid (to be deleted by the caller).
     * Must never throw — log and return emptyList on transport failure.
     */
    fun send(
        tokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String>,
        badge: Int?,
    ): List<String>
}
