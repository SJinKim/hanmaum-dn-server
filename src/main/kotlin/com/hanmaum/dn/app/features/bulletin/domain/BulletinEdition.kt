package com.hanmaum.dn.app.features.bulletin.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * One weekly 주보, at most one non-deleted edition per Sunday. Content fields are nullable because a draft may
 * be incomplete; publishing checks the required ones (HDN-146).
 */
@Entity
@Table(name = "bulletin_edition")
class BulletinEdition(
    /** Always a Sunday; the database rejects any other day. */
    @Column(name = "service_date", nullable = false)
    var serviceDate: LocalDate,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    var service: BulletinService,
    /** Keycloak subject of whoever created the draft. */
    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    val createdBy: String,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: BulletinStatus = BulletinStatus.DRAFT

    /** VOL number, assigned on first publish and kept on withdrawal (HDN-290). */
    @Column(name = "volume", unique = true)
    var volume: Int? = null

    // ─── Snapshot of the service, taken when the edition is created ──────────
    @Column(name = "service_name", length = 50)
    var serviceName: String? = null

    @Column(name = "service_start_time")
    var serviceStartTime: LocalTime? = null

    // ─── 순서 ─────────────────────────────────────────────────────────────────

    /** 대표기도 */
    @Column(name = "opening_prayer_by", length = 100)
    var openingPrayerBy: String? = null

    /** 헌금송 */
    @Column(name = "offering_song_by", length = 100)
    var offeringSongBy: String? = null

    /** 성경봉독 */
    @Column(name = "scripture_reference", length = 100)
    var scriptureReference: String? = null

    /** 말씀선포 */
    @Column(name = "sermon_title", length = 200)
    var sermonTitle: String? = null

    @Column(name = "sermon_preacher", length = 100)
    var sermonPreacher: String? = null

    /** 응답기도 */
    @Column(name = "response_prayer_by", length = 100)
    var responsePrayerBy: String? = null

    /** 응답찬양 */
    @Column(name = "response_song", length = 200)
    var responseSong: String? = null

    @Column(name = "published_at")
    var publishedAt: Instant? = null

    @Column(name = "withdrawn_at")
    var withdrawnAt: Instant? = null

    @Column(name = "updated_by", length = 64)
    var updatedBy: String? = null

    /** Optimistic lock: two admins editing the same draft must not overwrite each other. */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0

    /** 예배를 여는 찬양, in order. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "bulletin_song", joinColumns = [JoinColumn(name = "edition_id")])
    @OrderColumn(name = "position")
    @Column(name = "title", nullable = false, length = 200)
    val songs: MutableList<String> = mutableListOf()

    /** 교회소식, in order. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "bulletin_announcement", joinColumns = [JoinColumn(name = "edition_id")])
    @OrderColumn(name = "position")
    val announcements: MutableList<BulletinAnnouncement> = mutableListOf()

    /** 설교 나눔, in order. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "bulletin_sharing_block", joinColumns = [JoinColumn(name = "edition_id")])
    @OrderColumn(name = "position")
    val sharingBlocks: MutableList<BulletinSharingBlock> = mutableListOf()

    /** Stores the service's name and start as they are now; later edits to the service don't reach this edition. */
    fun snapshotService() {
        serviceName = service.name
        serviceStartTime = service.startTime
    }

    /**
     * Takes over every content field and list of [source], lists in their order. Date, status,
     * VOL, service and audit fields stay this edition's own (HDN-290).
     */
    fun copyContentFrom(source: BulletinEdition) {
        openingPrayerBy = source.openingPrayerBy
        offeringSongBy = source.offeringSongBy
        scriptureReference = source.scriptureReference
        sermonTitle = source.sermonTitle
        sermonPreacher = source.sermonPreacher
        responsePrayerBy = source.responsePrayerBy
        responseSong = source.responseSong
        songs.clear()
        songs.addAll(source.songs)
        announcements.clear()
        announcements.addAll(source.announcements.map { BulletinAnnouncement(it.title, it.body) })
        sharingBlocks.clear()
        sharingBlocks.addAll(source.sharingBlocks.map { BulletinSharingBlock(it.type, it.text, it.reference) })
    }

    /** [assignedVolume] is used only on the first publish; a republished edition keeps its VOL. */
    fun publish(
        assignedVolume: Int,
        now: Instant,
        by: String,
    ) {
        check(status != BulletinStatus.PUBLISHED) { "Edition is already published" }
        if (volume == null) volume = assignedVolume
        status = BulletinStatus.PUBLISHED
        publishedAt = now
        withdrawnAt = null
        updatedBy = by
    }

    fun withdraw(
        now: Instant,
        by: String,
    ) {
        check(status == BulletinStatus.PUBLISHED) { "Only a published edition can be withdrawn" }
        status = BulletinStatus.WITHDRAWN
        withdrawnAt = now
        updatedBy = by
    }
}
