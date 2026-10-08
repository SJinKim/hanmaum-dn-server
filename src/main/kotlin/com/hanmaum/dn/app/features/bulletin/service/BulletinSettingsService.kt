package com.hanmaum.dn.app.features.bulletin.service

import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinSectionTitleResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinServiceRequest
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinServiceResponse
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSectionKey
import com.hanmaum.dn.app.features.bulletin.domain.BulletinService
import com.hanmaum.dn.app.features.bulletin.repository.BulletinEditionRepository
import com.hanmaum.dn.app.features.bulletin.repository.BulletinSectionTitleRepository
import com.hanmaum.dn.app.features.bulletin.repository.BulletinServiceRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.util.UUID

/** 주보 설정: the services an edition can belong to and the renamable section titles (HDN-146). */
@Service
class BulletinSettingsService(
    private val services: BulletinServiceRepository,
    private val editions: BulletinEditionRepository,
    private val sectionTitles: BulletinSectionTitleRepository,
    private val clock: Clock,
) {
    // ─── services ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listServices(): List<BulletinServiceResponse> =
        services.findAllByDeletedAtIsNullOrderBySortOrderAsc().map { BulletinServiceResponse.from(it) }

    @Transactional
    fun createService(
        request: BulletinServiceRequest,
        by: String,
    ): BulletinServiceResponse {
        requireActiveIfDefault(request)
        if (request.isBulletinDefault) clearDefault()
        val service =
            BulletinService(
                name = request.name.trim(),
                startTime = request.startTime,
                sortOrder = request.sortOrder,
                active = request.active,
                isBulletinDefault = request.isBulletinDefault,
                createdBy = by,
                updatedBy = by,
            )
        return BulletinServiceResponse.from(services.saveAndFlush(service))
    }

    /**
     * Editing a service never reaches existing editions; they keep their snapshot. The default
     * can only move to another service, not vanish: a new draft needs one.
     */
    @Transactional
    fun updateService(
        publicId: UUID,
        request: BulletinServiceRequest,
        by: String,
    ): BulletinServiceResponse {
        val service = findService(publicId)
        if (service.isBulletinDefault && !request.isBulletinDefault) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Mark another service as default instead")
        }
        requireActiveIfDefault(request)
        if (request.isBulletinDefault && !service.isBulletinDefault) clearDefault()

        service.name = request.name.trim()
        service.startTime = request.startTime
        service.sortOrder = request.sortOrder
        service.active = request.active
        service.isBulletinDefault = request.isBulletinDefault
        service.updatedBy = by
        return BulletinServiceResponse.from(services.saveAndFlush(service))
    }

    /**
     * Soft-deletes an unused service. One that an edition points at is only deactivated, so
     * old editions keep a valid reference; the response says which of the two happened.
     */
    @Transactional
    fun deleteService(
        publicId: UUID,
        by: String,
    ): BulletinServiceResponse? {
        val service = findService(publicId)
        if (service.isBulletinDefault) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "The default service cannot be deleted")
        }
        service.updatedBy = by
        if (editions.existsByService(service)) {
            service.active = false
            return BulletinServiceResponse.from(services.saveAndFlush(service))
        }
        service.deletedAt = clock.instant()
        services.saveAndFlush(service)
        return null
    }

    // ─── section titles ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listSectionTitles(): List<BulletinSectionTitleResponse> =
        sectionTitles.findAll().sortedBy { it.key.ordinal }.map { BulletinSectionTitleResponse.from(it) }

    /** A blank [title] goes back to the default title. */
    @Transactional
    fun updateSectionTitle(
        key: BulletinSectionKey,
        title: String?,
        by: String,
    ): BulletinSectionTitleResponse {
        val section =
            sectionTitles.findById(key).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found")
            }
        section.title = title?.trim()?.ifEmpty { null } ?: section.defaultTitle
        section.updatedBy = by
        return BulletinSectionTitleResponse.from(sectionTitles.saveAndFlush(section))
    }

    /** The partial unique index allows one default, so the old one is cleared and flushed first. */
    private fun clearDefault() {
        services.findByIsBulletinDefaultTrueAndDeletedAtIsNull()?.let {
            it.isBulletinDefault = false
            services.saveAndFlush(it)
        }
    }

    private fun findService(publicId: UUID): BulletinService =
        services.findByPublicIdAndDeletedAtIsNull(publicId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found")

    /** Checks the requested state, not the stored one, so promoting an inactive service fails too. */
    private fun requireActiveIfDefault(request: BulletinServiceRequest) {
        if (request.isBulletinDefault && !request.active) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "The default service cannot be deactivated")
        }
    }
}
