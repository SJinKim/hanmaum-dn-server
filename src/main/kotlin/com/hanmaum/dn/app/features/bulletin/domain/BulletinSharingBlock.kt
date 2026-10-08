package com.hanmaum.dn.app.features.bulletin.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

/** One block of 설교 나눔. The database rejects a reference on anything but SCRIPTURE. */
@Embeddable
class BulletinSharingBlock(
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: BulletinSharingBlockType,
    @Column(name = "text", nullable = false, length = 2000)
    var text: String,
    @Column(name = "reference", length = 100)
    var reference: String? = null,
)
