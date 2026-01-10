package com.hanmaum.dn.app.features.members.domain

import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "members")
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY )
    val id: Long? = null,

    // --- NAMEN ---
    @Column(name = "last_name", nullable = false )
    var lastName: String,

    @Column(name = "first_name", nullable = false)
    var firstName: String,

    var discriminator: String? = null,

    // --- STAMMDATEN ---
    @Enumerated(EnumType.STRING)
    @Column(length = 3)
    var gender: Gender? = null,

    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,

    @Column(name = "phone_number")
    var phoneNumber: String? = null,

    var email: String? = null,

    // --- ADRESSE (Neu strukturiert) ---
    var street: String? = null,

    @Column(name = "zip_code")
    var zipCode: String? = null,

    var city: String? = null,

    // --- KIRCHEN DATEN ---
    @Column(name = "registration_date")
    var registrationDate: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "member_status", nullable = false)
    var memberStatus: MemberStatus = MemberStatus.ACTIVE,

    var role: String? = null, // "직분? or 사역?"

    // --- BEZIEHUNG ZUR GRUPPE (Foreign Key) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    var group: ChurchGroup? = null,

) : BaseEntity() {
    // Convenience Methode für vollen Namen
    fun getFullName(): String {
        return "$lastName$firstName" // Koreanisch: Keine Leerstelle
    }
}