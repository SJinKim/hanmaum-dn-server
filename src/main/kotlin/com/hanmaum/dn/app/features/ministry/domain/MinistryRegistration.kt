package com.hanmaum.dn.app.features.ministry.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "ministry_registrations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_ministry_member_period",
            columnNames = ["ministry_id", "member_id", "registration_period"],
        ),
    ],
)
class MinistryRegistration(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ministry_id", nullable = false)
    var ministry: Ministry,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    var member: Member,
    @Column(name = "registration_period", nullable = false, length = 4)
    var registrationPeriod: String,
    @Column(columnDefinition = "TEXT", length = 500)
    var note: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 20)
    var status: RegistrationStatus = RegistrationStatus.PENDING,
) : BaseEntity() {
    @Column(name = "delete_entry_at")
    var deleteEntryAt: Instant? = null
}
