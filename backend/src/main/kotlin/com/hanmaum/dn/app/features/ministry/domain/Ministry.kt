package com.hanmaum.dn.app.features.ministry.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.*

@Entity
@Table(name = "ministries")
class Ministry (

    @Column(nullable = false)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var description: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_member_id")
    var leader: Member? = null,

    var imageUrl: String? = null,

    @Column(name = "is_ministry_active")
    var isMinistryActive: Boolean = true

) : BaseEntity()