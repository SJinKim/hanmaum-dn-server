package com.hanmaum.dn.app.common.jpa

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@MappedSuperclass
abstract class BaseEntity {

    @CreationTimestamp
    @Column(name="created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name="updated_at")
    var updatedAt: LocalDateTime? = null

    @Column(name="deleted_at")
    var deletedAt: LocalDateTime? = null

    fun isActive(): Boolean = deletedAt == null
}