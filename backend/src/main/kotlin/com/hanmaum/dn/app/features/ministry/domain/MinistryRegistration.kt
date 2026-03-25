package com.hanmaum.dn.app.features.ministry.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "ministry_registrations",
    uniqueConstraints =[
        // Verhindert doppelte Anmeldungen im selben Jahr für denselben Dienst
        UniqueConstraint(
            name = "uq_ministry_member_period",
            columnNames =["ministry_id", "member_id", "registration_period"]
        )
    ]
)
class MinistryRegistration (

    // Auf welchen Dienst bezieht sich die Anmeldung?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ministry_id", nullable = false)
    var ministry: Ministry,

    // Wer meldet sich an? (Hier reicht uns oft die ID, da wir sie aus dem Security-Token bekommen)
    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    // Das optionale Notizfeld ("Ich spiele Gitarre")
    @Column(columnDefinition = "TEXT")
    var note: String? = null,

    // Dein Jahres-Feature (z.B. "2026")
    @Column(name = "registration_period", nullable = false)
    var registrationPeriod: String

) : BaseEntity()