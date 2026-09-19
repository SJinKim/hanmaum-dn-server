package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.features.newcomers.api.v1.dto.CreateFormLinkRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.CreateNewcomerRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.FormLinkResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.PublicFormMetadataResponse
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.PublicNewcomerSubmissionRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.PublicSubmissionResponse
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerFormLink
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerFormSubmission
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerFormLinkRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerFormSubmissionRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class NewcomerFormService(
    private val linkRepository: NewcomerFormLinkRepository,
    private val submissionRepository: NewcomerFormSubmissionRepository,
    private val profileRepository: NewcomerProfileRepository,
    private val newcomerService: NewcomerService,
    @Value("\${app.newcomer-form.consent-version:v1}") private val consentVersion: String,
    @Value("\${app.newcomer-form.rate-limit-per-minute:10}") private val rateLimitPerMinute: Int,
) {
    private val random = SecureRandom()
    private val attempts = ConcurrentHashMap<String, ArrayDeque<Instant>>()

    @Transactional
    fun createLink(
        request: CreateFormLinkRequest,
        actorSubject: String,
    ): FormLinkResponse {
        val now = Instant.now()
        val expiresAt = request.expiresAt ?: now.plus(Duration.ofHours(12))
        if (!expiresAt.isAfter(now) || expiresAt.isAfter(now.plus(Duration.ofHours(24)))) {
            throw NewcomerException(HttpStatus.BAD_REQUEST, "expiresAt must be in the future and no more than 24 hours away.")
        }
        val token = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val link = linkRepository.save(NewcomerFormLink(hash(token), expiresAt, actorSubject))
        return link.toResponse(token)
    }

    @Transactional(readOnly = true)
    fun listLinks(): List<FormLinkResponse> = linkRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc().map { it.toResponse() }

    @Transactional
    fun revoke(publicId: UUID): FormLinkResponse {
        val link = linkRepository.findByPublicIdAndDeletedAtIsNull(publicId) ?: throw EntityNotFoundException("Form link not found")
        if (link.revokedAt == null) link.revokedAt = Instant.now()
        return linkRepository.save(link).toResponse()
    }

    @Transactional(readOnly = true)
    fun metadata(token: String): PublicFormMetadataResponse {
        val link = requireActive(linkRepository.findByTokenHashAndDeletedAtIsNull(hash(token)))
        return PublicFormMetadataResponse(link.expiresAt, consentVersion)
    }

    @Transactional
    fun submit(
        token: String,
        idempotencyKey: String,
        remoteAddress: String,
        request: PublicNewcomerSubmissionRequest,
    ): PublicSubmissionResponse {
        enforceRateLimit(hash(token) + ":" + remoteAddress)
        validatePublicPayload(idempotencyKey, request)
        val link = requireActive(linkRepository.findByTokenHashForUpdate(hash(token)))
        val idempotencyHash = hash(idempotencyKey)
        submissionRepository.findByFormLinkIdAndIdempotencyHash(link.id!!, idempotencyHash)?.let {
            return PublicSubmissionResponse(it.newcomerProfile.publicId.toString(), it.createdAt)
        }
        val created =
            try {
                newcomerService.create(
                    CreateNewcomerRequest(
                        lastName = request.lastName,
                        firstName = request.firstName,
                        englishName = request.englishName,
                        gender = request.gender,
                        birthDate = request.birthDate,
                        email = request.email,
                        phoneNumber = request.phoneNumber,
                        street = request.street,
                        houseNumber = request.houseNumber,
                        zipCode = request.zipCode,
                        city = request.city,
                        baptism = request.baptism,
                        churchExperience = request.churchExperience,
                        visitMotives = request.visitMotives,
                        kakaoId = request.kakaoId,
                        previousChurch = request.previousChurch,
                    ),
                )
            } catch (e: NewcomerException) {
                if (e.status == HttpStatus.CONFLICT) {
                    throw NewcomerException(HttpStatus.BAD_REQUEST, "The submission could not be accepted.")
                }
                throw e
            }
        val profile =
            profileRepository
                .findByPublicIdAndDeletedAtIsNull(UUID.fromString(created.publicId))
                .orElseThrow { IllegalStateException("Created newcomer profile could not be reloaded") }
        profile.consentVersion = consentVersion
        profile.consentedAt = Instant.now()
        profileRepository.save(profile)
        val submission = submissionRepository.save(NewcomerFormSubmission(link, profile, idempotencyHash))
        link.useCount++
        linkRepository.save(link)
        return PublicSubmissionResponse(profile.publicId.toString(), submission.createdAt)
    }

    private fun validatePublicPayload(
        idempotencyKey: String,
        request: PublicNewcomerSubmissionRequest,
    ) {
        if (idempotencyKey.length !in
            8..200
        ) {
            throw NewcomerException(HttpStatus.BAD_REQUEST, "Idempotency-Key must contain 8 to 200 characters.")
        }
        if (!request.honeypot.isNullOrBlank()) throw NewcomerException(HttpStatus.BAD_REQUEST, "The submission could not be accepted.")
        val payloadSize =
            listOfNotNull(
                request.lastName,
                request.firstName,
                request.englishName,
                request.phoneNumber,
                request.email,
                request.kakaoId,
                request.street,
                request.houseNumber,
                request.zipCode,
                request.city,
                request.previousChurch,
            ).sumOf(String::length) + request.visitMotives.sumOf(String::length)
        if (payloadSize > 32_000) throw NewcomerException(HttpStatus.BAD_REQUEST, "The submission is too large.")
    }

    private fun requireActive(link: NewcomerFormLink?): NewcomerFormLink {
        if (link == null || !link.isActive(Instant.now())) {
            throw NewcomerException(HttpStatus.NOT_FOUND, "The form link is unavailable.")
        }
        return link
    }

    private fun enforceRateLimit(key: String) {
        val now = Instant.now()
        val queue = attempts.computeIfAbsent(key) { ArrayDeque() }
        synchronized(queue) {
            while (queue.firstOrNull()?.isBefore(now.minusSeconds(60)) == true) queue.removeFirst()
            if (queue.size >=
                rateLimitPerMinute
            ) {
                throw NewcomerException(HttpStatus.TOO_MANY_REQUESTS, "Too many submissions. Try again later.")
            }
            queue.addLast(now)
        }
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

private fun NewcomerFormLink.toResponse(token: String? = null) =
    FormLinkResponse(publicId.toString(), expiresAt, revokedAt, isActive(Instant.now()), useCount, token)
