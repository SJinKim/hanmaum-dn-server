package com.hanmaum.dn.app.features.ministry.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class MinistryContact(
    @Column(name = "role", nullable = false, length = 50)
    var role: String,
    @Column(name = "name", nullable = false, length = 150)
    var name: String,
)
