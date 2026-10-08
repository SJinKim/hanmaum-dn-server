package com.hanmaum.dn.app.features.bulletin.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

/**
 * Renamable title of a fixed 주보 section. Rows are seeded and never created or deleted
 * at runtime, so this does not extend BaseEntity: the key is the identity.
 */
@Entity
@Table(name = "bulletin_section_title")
class BulletinSectionTitle(
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "key", nullable = false, length = 40, updatable = false)
    val key: BulletinSectionKey,
    @Column(name = "title", nullable = false, length = 50)
    var title: String,
    @Column(name = "default_title", nullable = false, length = 50, updatable = false)
    val defaultTitle: String,
    @Column(name = "updated_by", length = 64)
    var updatedBy: String? = null,
) {
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at")
    var updatedAt: Instant? = null

    @PreUpdate
    fun touchUpdatedAt() {
        updatedAt = Instant.now()
    }

    override fun equals(other: Any?): Boolean = this === other || (other is BulletinSectionTitle && key == other.key)

    override fun hashCode(): Int = key.hashCode()
}
