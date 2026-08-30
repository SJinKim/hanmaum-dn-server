package com.hanmaum.dn.app.features.announcements.api.v1.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Announcement data returned to app and admin clients.
 *
 * @property id Public announcement identifier.
 * @property title Display title.
 * @property body Full announcement content.
 * @property category Announcement category name.
 * @property startAt ISO-8601 start of the visibility window.
 * @property endAt ISO-8601 end of the visibility window, if limited.
 * @property imageUrl Optional preview or hero image URL.
 * @property location Optional event or meeting location.
 * @property viewCount Number of successful detail views.
 * @property isPinned Whether the announcement is pinned.
 */
data class AnnouncementDto(
    val id: String,
    val title: String,
    val body: String,
    val category: String,
    val startAt: String,
    @field:Schema(types = ["string", "null"])
    val endAt: String?,
    @field:Schema(types = ["string", "null"])
    val imageUrl: String?,
    @field:Schema(types = ["string", "null"])
    val location: String?,
    val viewCount: Long,
    val isPinned: Boolean,
)

/**
 * Creates an announcement.
 *
 * @property title Display title.
 * @property body Full announcement content.
 * @property startAt Start of the visibility window.
 * @property endAt End of the visibility window, if limited.
 * @property imageUrl Optional preview or hero image URL.
 * @property location Optional event or meeting location.
 * @property isPinned Whether the announcement is pinned.
 * @property category Announcement category name.
 */
data class CreateAnnouncementRequest(
    val title: String,
    val body: String,
    val startAt: OffsetDateTime = OffsetDateTime.now(ZoneId.of("Europe/Berlin")),
    @field:Schema(types = ["string", "null"], format = "date-time")
    val endAt: OffsetDateTime? = null,
    @field:Schema(types = ["string", "null"])
    val imageUrl: String? = null,
    @field:Schema(types = ["string", "null"])
    val location: String? = null,
    val isPinned: Boolean = false,
    val category: String,
)

/**
 * Fully replaces editable announcement data.
 *
 * @property title Display title.
 * @property body Full announcement content.
 * @property startAt Start of the visibility window.
 * @property endAt End of the visibility window; `null` clears it.
 * @property imageUrl Preview or hero image URL; `null` clears it.
 * @property location Event or meeting location; `null` clears it.
 * @property isPinned Whether the announcement is pinned.
 * @property category Announcement category name.
 */
data class UpdateAnnouncementRequest(
    val title: String,
    val body: String,
    val startAt: OffsetDateTime,
    @field:Schema(types = ["string", "null"], format = "date-time")
    val endAt: OffsetDateTime? = null,
    @field:Schema(types = ["string", "null"])
    val imageUrl: String? = null,
    @field:Schema(types = ["string", "null"])
    val location: String? = null,
    val isPinned: Boolean = false,
    val category: String,
)
