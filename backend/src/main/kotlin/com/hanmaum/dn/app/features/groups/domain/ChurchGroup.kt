package com.hanmaum.dn.app.features.groups.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(name = "church_groups", uniqueConstraints = [UniqueConstraint(columnNames = ["division", "name"])])
class ChurchGroup(
    @Column(nullable = false)
    var division: String? = null,
    @Column(nullable = false)
    var name: String,
) : BaseEntity() {
    fun getFullName(): String = if (division != null) "$division - $name" else name
}
