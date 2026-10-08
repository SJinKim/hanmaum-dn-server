package com.hanmaum.dn.app.features.bulletin.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** One 교회소식 entry. */
@Embeddable
class BulletinAnnouncement(
    @Column(name = "title", nullable = false, length = 200)
    var title: String,
    @Column(name = "body", length = 2000)
    var body: String? = null,
)
