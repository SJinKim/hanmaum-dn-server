package com.hanmaum.dn.app.features.bulletin.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalTime

/**
 * A 예배 a 주보 can belong to (1부/2부/3부). Kept apart from attendance definitions on
 * purpose: a check-in window is not a service start.
 */
@Entity
@Table(name = "bulletin_service")
class BulletinService(
    @Column(name = "name", nullable = false, length = 50)
    var name: String,
    /** Wall-clock start in Europe/Berlin. */
    @Column(name = "start_time", nullable = false)
    var startTime: LocalTime,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
    /** False once retired; referenced services are deactivated rather than deleted. */
    @Column(name = "active", nullable = false)
    var active: Boolean = true,
    /** Preselected for a new edition; a partial unique index allows only one. */
    @Column(name = "is_bulletin_default", nullable = false)
    var isBulletinDefault: Boolean = false,
    /** Keycloak subject; null for seeded rows. */
    @Column(name = "created_by", length = 64, updatable = false)
    val createdBy: String? = null,
    @Column(name = "updated_by", length = 64)
    var updatedBy: String? = null,
) : BaseEntity()
