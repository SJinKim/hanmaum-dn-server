package com.hanmaum.dn.app.features.training.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "training")
class Training(
    @Column(name = "name", nullable = false, length = 100)
    var name: String,
    /** Progression order; the members grid surfaces the highest completed sort_order. */
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
) : BaseEntity()
