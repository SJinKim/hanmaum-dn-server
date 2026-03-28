package com.hanmaum.dn.app.features.ministry.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "ministries")
class Ministry(
    @Column(name = "name", nullable = false, length = 100)
    var name: String,
    /** Short one-liner shown in list cards. Max 200 chars. */
    @Column(name = "short_description", nullable = false, length = 200)
    var shortDescription: String,
    /** Full markdown/text body shown on the detail page. Optional. */
    @Column(name = "long_description", columnDefinition = "TEXT")
    var longDescription: String? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_member_id")
    var leader: Member? = null,
    @Column(name = "image_url")
    var imageUrl: String? = null,
    @Column(name = "is_ministry_active", nullable = false)
    var isMinistryActive: Boolean = true,
) : BaseEntity()
