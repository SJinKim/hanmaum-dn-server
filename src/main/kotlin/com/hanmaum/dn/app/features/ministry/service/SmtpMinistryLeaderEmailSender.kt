package com.hanmaum.dn.app.features.ministry.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class SmtpMinistryLeaderEmailSender(
    @Value("\${KEYCLOAK_SMTP_HOST:}") private val host: String,
    @Value("\${KEYCLOAK_SMTP_PORT:587}") private val port: Int,
    @Value("\${KEYCLOAK_SMTP_FROM:}") private val from: String,
    @Value("\${KEYCLOAK_SMTP_STARTTLS:true}") private val startTls: Boolean,
    @Value("\${KEYCLOAK_SMTP_SSL:false}") private val ssl: Boolean,
    @Value("\${KEYCLOAK_SMTP_AUTH:true}") private val auth: Boolean,
    @Value("\${KEYCLOAK_SMTP_USER:}") private val username: String,
    @Value("\${KEYCLOAK_SMTP_PASSWORD:}") private val password: String,
) : MinistryLeaderEmailSender {
    override fun sendApplication(
        leaderEmail: String,
        ministryName: String,
        applicantName: String,
        selfIntroduction: String,
    ) {
        if (host.isBlank() || from.isBlank() || (auth && (username.isBlank() || password.isBlank()))) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ministry application email is not configured")
        }
        val sender = JavaMailSenderImpl()
        sender.host = host
        sender.port = port
        sender.username = username
        sender.password = password
        sender.javaMailProperties["mail.smtp.auth"] = auth.toString()
        sender.javaMailProperties["mail.smtp.starttls.enable"] = startTls.toString()
        sender.javaMailProperties["mail.smtp.ssl.enable"] = ssl.toString()
        sender.javaMailProperties["mail.smtp.connectiontimeout"] = "5000"
        sender.javaMailProperties["mail.smtp.timeout"] = "5000"
        sender.javaMailProperties["mail.smtp.writetimeout"] = "5000"
        val mail = SimpleMailMessage()
        mail.setFrom(from)
        mail.setTo(leaderEmail)
        mail.subject = "[$ministryName] 새 사역 신청"
        mail.text = "$applicantName 님이 사역에 신청했습니다.\n\n자기 소개:\n$selfIntroduction"
        try {
            sender.send(mail)
        } catch (e: MailException) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not notify the ministry leader", e)
        }
    }
}
