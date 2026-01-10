package com.hanmaum.dn.app.features.groups.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(name = "church_groups", uniqueConstraints = [UniqueConstraint(columnNames = ["division", "name"])])
class ChurchGroup (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    var division: String? = null,

    @Column(nullable = false)
    var name: String,

    @Column(name = "created_at")
    var createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun getFullName(): String {
        return if (division != null) "$division - $name" else name
    }
}