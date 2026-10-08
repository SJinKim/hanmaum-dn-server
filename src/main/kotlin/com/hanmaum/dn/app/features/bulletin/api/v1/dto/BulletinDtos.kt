package com.hanmaum.dn.app.features.bulletin.api.v1.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.hanmaum.dn.app.features.bulletin.domain.BulletinAnnouncement
import com.hanmaum.dn.app.features.bulletin.domain.BulletinEdition
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSectionKey
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSectionTitle
import com.hanmaum.dn.app.features.bulletin.domain.BulletinService
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSharingBlock
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSharingBlockType
import com.hanmaum.dn.app.features.bulletin.domain.BulletinStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// ─── Requests ─────────────────────────────────────────────────────────────────

/** Every field optional: the server fills in the next free Sunday and the default service. */
data class CreateBulletinRequest(
    val serviceDate: LocalDate? = null,
    val servicePublicId: UUID? = null,
    /** Edition whose content the new draft takes over, usually last week's. */
    val copyFrom: UUID? = null,
)

data class BulletinAnnouncementDto(
    @field:NotBlank(message = "소식 제목은 필수입니다.")
    @field:Size(max = 200, message = "소식 제목은 최대 200자입니다.")
    val title: String,
    @field:Size(max = 2000, message = "소식 내용은 최대 2000자입니다.")
    val body: String? = null,
)

/** [reference] belongs to SCRIPTURE only; the service rejects it on any other type. */
data class BulletinSharingBlockDto(
    @field:NotNull(message = "블록 종류는 필수입니다.")
    val type: BulletinSharingBlockType,
    @field:NotBlank(message = "블록 내용은 필수입니다.")
    @field:Size(max = 2000, message = "블록 내용은 최대 2000자입니다.")
    val text: String,
    @field:Size(max = 100, message = "성경 구절은 최대 100자입니다.")
    val reference: String? = null,
)

/**
 * Full replacement of a draft's content. Date, service and status are not part of it; they
 * have their own endpoints or are fixed at creation. [version] is the one the client loaded,
 * a stale one is a 409.
 */
data class UpdateBulletinRequest(
    @field:NotNull(message = "version은 필수입니다.")
    val version: Long,
    @field:Size(max = 100)
    val openingPrayerBy: String? = null,
    @field:Size(max = 100)
    val offeringSongBy: String? = null,
    @field:Size(max = 100)
    val scriptureReference: String? = null,
    @field:Size(max = 200)
    val sermonTitle: String? = null,
    @field:Size(max = 100)
    val sermonPreacher: String? = null,
    @field:Size(max = 100)
    val responsePrayerBy: String? = null,
    @field:Size(max = 200)
    val responseSong: String? = null,
    @field:Size(max = 8, message = "찬양은 최대 8곡입니다.")
    val songs: List<
        @NotBlank(message = "찬양 제목은 비어 있을 수 없습니다.")
        @Size(max = 200, message = "찬양 제목은 최대 200자입니다.")
        String,
    > = emptyList(),
    @field:Valid
    @field:Size(max = 20, message = "교회소식은 최대 20개입니다.")
    val announcements: List<BulletinAnnouncementDto> = emptyList(),
    @field:Valid
    @field:Size(max = 50, message = "설교 나눔은 최대 50개 블록입니다.")
    val sharingBlocks: List<BulletinSharingBlockDto> = emptyList(),
)

data class BulletinServiceRequest(
    @field:NotBlank(message = "예배 이름은 필수입니다.")
    @field:Size(max = 50, message = "예배 이름은 최대 50자입니다.")
    val name: String,
    @field:NotNull(message = "시작 시간은 필수입니다.")
    val startTime: LocalTime,
    @field:Min(0)
    @field:Max(999)
    val sortOrder: Int = 0,
    val active: Boolean = true,
    /** Setting this moves the default here; the previous default loses it. */
    @get:JsonProperty("isBulletinDefault")
    val isBulletinDefault: Boolean = false,
)

/** A blank [title] resets the section to its default title. */
data class UpdateSectionTitleRequest(
    @field:Size(max = 50, message = "제목은 최대 50자입니다.")
    val title: String? = null,
)

// ─── Responses ────────────────────────────────────────────────────────────────

data class BulletinServiceResponse(
    val publicId: UUID,
    val name: String,
    val startTime: LocalTime,
    val sortOrder: Int,
    val active: Boolean,
    @get:JsonProperty("isBulletinDefault")
    val isBulletinDefault: Boolean,
) {
    companion object {
        fun from(service: BulletinService) =
            BulletinServiceResponse(
                publicId = service.publicId,
                name = service.name,
                startTime = service.startTime,
                sortOrder = service.sortOrder,
                active = service.active,
                isBulletinDefault = service.isBulletinDefault,
            )
    }
}

data class BulletinSectionTitleResponse(
    val key: BulletinSectionKey,
    val title: String,
    val defaultTitle: String,
) {
    companion object {
        fun from(title: BulletinSectionTitle) = BulletinSectionTitleResponse(title.key, title.title, title.defaultTitle)
    }
}

/** What a new draft starts with: the next free Sunday and the default service. */
data class BulletinDefaultsResponse(
    val serviceDate: LocalDate,
    val service: BulletinServiceResponse,
)

/** One row of the admin list. */
data class BulletinEditionSummary(
    val publicId: UUID,
    val serviceDate: LocalDate,
    val volume: Int?,
    val status: BulletinStatus,
    val sermonTitle: String?,
    val serviceName: String?,
    val publishedAt: Instant?,
) {
    companion object {
        fun from(edition: BulletinEdition) =
            BulletinEditionSummary(
                publicId = edition.publicId,
                serviceDate = edition.serviceDate,
                volume = edition.volume,
                status = edition.status,
                sermonTitle = edition.sermonTitle,
                serviceName = edition.serviceName,
                publishedAt = edition.publishedAt,
            )
    }
}

/**
 * A whole edition. Service name and start are the snapshot taken at creation; section titles
 * are the current ones, so a rename reaches past editions too.
 */
data class BulletinEditionResponse(
    val publicId: UUID,
    val serviceDate: LocalDate,
    val volume: Int?,
    val status: BulletinStatus,
    val servicePublicId: UUID,
    val serviceName: String?,
    val serviceStartTime: LocalTime?,
    val openingPrayerBy: String?,
    val offeringSongBy: String?,
    val scriptureReference: String?,
    val sermonTitle: String?,
    val sermonPreacher: String?,
    val responsePrayerBy: String?,
    val responseSong: String?,
    val songs: List<String>,
    val announcements: List<BulletinAnnouncementDto>,
    val sharingBlocks: List<BulletinSharingBlockDto>,
    val sectionTitles: List<BulletinSectionTitleResponse>,
    val publishedAt: Instant?,
    val withdrawnAt: Instant?,
    val version: Long,
) {
    companion object {
        /** Touches the lazy lists, so call it inside the transaction. */
        fun from(
            edition: BulletinEdition,
            sectionTitles: List<BulletinSectionTitleResponse>,
        ) = BulletinEditionResponse(
            publicId = edition.publicId,
            serviceDate = edition.serviceDate,
            volume = edition.volume,
            status = edition.status,
            servicePublicId = edition.service.publicId,
            serviceName = edition.serviceName,
            serviceStartTime = edition.serviceStartTime,
            openingPrayerBy = edition.openingPrayerBy,
            offeringSongBy = edition.offeringSongBy,
            scriptureReference = edition.scriptureReference,
            sermonTitle = edition.sermonTitle,
            sermonPreacher = edition.sermonPreacher,
            responsePrayerBy = edition.responsePrayerBy,
            responseSong = edition.responseSong,
            songs = edition.songs.toList(),
            announcements = edition.announcements.map { it.toDto() },
            sharingBlocks = edition.sharingBlocks.map { it.toDto() },
            sectionTitles = sectionTitles,
            publishedAt = edition.publishedAt,
            withdrawnAt = edition.withdrawnAt,
            version = edition.version,
        )
    }
}

fun BulletinAnnouncement.toDto() = BulletinAnnouncementDto(title, body)

fun BulletinSharingBlock.toDto() = BulletinSharingBlockDto(type, text, reference)

fun BulletinAnnouncementDto.toEntity() = BulletinAnnouncement(title.trim(), body?.trim()?.ifEmpty { null })

fun BulletinSharingBlockDto.toEntity() = BulletinSharingBlock(type, text.trim(), reference?.trim()?.ifEmpty { null })
