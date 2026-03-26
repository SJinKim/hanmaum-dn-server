package com.hanmaum.dn.app.features.members.domain

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "members")
class Member(
    // --- NAMEN ---
    @Column(name = "last_name", nullable = false)
    var lastName: String,
    @Column(name = "first_name", nullable = false)
    var firstName: String,
    var discriminator: String? = null,
    // --- STAMMDATEN ---
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    var gender: Gender? = null,
    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,
    @Column(name = "phone_number")
    var phoneNumber: String? = null,
    var email: String? = null,
    // --- ADRESSE ---
    var street: String? = null,
    @Column(name = "zip_code")
    var zipCode: String? = null,
    var city: String? = null,
    // --- KIRCHEN DATEN ---
    @Column(name = "registration_date")
    var registrationDate: LocalDate? = LocalDate.now(),
    @Enumerated(EnumType.STRING)
    @Column(name = "member_status", nullable = false)
    var memberStatus: MemberStatus = MemberStatus.ACTIVE,
    /** Church position / title (직분). Not the app access role (that lives in Keycloak). */
    @Column(name = "role")
    var churchRole: String? = null,
    // --- BEZIEHUNG ZUR GRUPPE ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    var group: ChurchGroup? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "baptism")
    var baptism: Baptism? = null,
    // --- AUTH ---

    /** Keycloak subject UUID. Populated on registerMember(); null for admin-created members. */
    @Column(name = "keycloak_id", unique = true)
    var keycloakId: String? = null,
    // --- PROFILE ---
    @Column(name = "profile_image_url", length = 500)
    var profileImageUrl: String? = null,
) : BaseEntity() {
    fun getFullName(): String = "$lastName$firstName" // Korean: no space between surname and given name
}
