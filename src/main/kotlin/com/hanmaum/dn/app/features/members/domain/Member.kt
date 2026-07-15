package com.hanmaum.dn.app.features.members.domain

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.common.pii.EncryptedBaptismFieldConverter
import com.hanmaum.dn.app.common.pii.EncryptedBirthDateConverter
import com.hanmaum.dn.app.common.pii.EncryptedChurchRoleConverter
import com.hanmaum.dn.app.common.pii.EncryptedCityConverter
import com.hanmaum.dn.app.common.pii.EncryptedDiscriminatorConverter
import com.hanmaum.dn.app.common.pii.EncryptedEmailConverter
import com.hanmaum.dn.app.common.pii.EncryptedFirstNameConverter
import com.hanmaum.dn.app.common.pii.EncryptedGenderFieldConverter
import com.hanmaum.dn.app.common.pii.EncryptedHouseNumberConverter
import com.hanmaum.dn.app.common.pii.EncryptedKeycloakIdConverter
import com.hanmaum.dn.app.common.pii.EncryptedLastNameConverter
import com.hanmaum.dn.app.common.pii.EncryptedPhoneNumberConverter
import com.hanmaum.dn.app.common.pii.EncryptedProfileImageUrlConverter
import com.hanmaum.dn.app.common.pii.EncryptedRegistrationDateConverter
import com.hanmaum.dn.app.common.pii.EncryptedStreetConverter
import com.hanmaum.dn.app.common.pii.EncryptedZipCodeConverter
import com.hanmaum.dn.app.common.pii.PiiCryptoContext
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "members")
class Member(
    // --- NAMEN ---
    @Convert(converter = EncryptedLastNameConverter::class)
    @Column(name = "last_name", nullable = false, columnDefinition = "TEXT")
    var lastName: String,
    @Convert(converter = EncryptedFirstNameConverter::class)
    @Column(name = "first_name", nullable = false, columnDefinition = "TEXT")
    var firstName: String,
    @Convert(converter = EncryptedDiscriminatorConverter::class)
    @Column(columnDefinition = "TEXT")
    var discriminator: String? = null,
    // --- STAMMDATEN ---
    @Convert(converter = EncryptedGenderFieldConverter::class)
    @Column(columnDefinition = "TEXT")
    var gender: Gender? = null,
    @Convert(converter = EncryptedBirthDateConverter::class)
    @Column(name = "birth_date", columnDefinition = "TEXT")
    var birthDate: LocalDate? = null,
    @Convert(converter = EncryptedPhoneNumberConverter::class)
    @Column(name = "phone_number", columnDefinition = "TEXT")
    var phoneNumber: String? = null,
    @Convert(converter = EncryptedEmailConverter::class)
    @Column(columnDefinition = "TEXT")
    var email: String? = null,
    // --- ADRESSE ---
    @Convert(converter = EncryptedStreetConverter::class)
    @Column(columnDefinition = "TEXT")
    var street: String? = null,
    @Convert(converter = EncryptedHouseNumberConverter::class)
    @Column(name = "house_number", columnDefinition = "TEXT")
    var houseNumber: String? = null,
    @Convert(converter = EncryptedZipCodeConverter::class)
    @Column(name = "zip_code", columnDefinition = "TEXT")
    var zipCode: String? = null,
    @Convert(converter = EncryptedCityConverter::class)
    @Column(columnDefinition = "TEXT")
    var city: String? = null,
    // --- KIRCHEN DATEN ---
    @Convert(converter = EncryptedRegistrationDateConverter::class)
    @Column(name = "registration_date", columnDefinition = "TEXT")
    var registrationDate: LocalDate? = LocalDate.now(),
    @Enumerated(EnumType.STRING)
    @Column(name = "member_status", nullable = false)
    var memberStatus: MemberStatus = MemberStatus.PENDING,
    /** Church position / title (직분). Not the app access role (that lives in Keycloak). */
    @Convert(converter = EncryptedChurchRoleConverter::class)
    @Column(name = "role", columnDefinition = "TEXT")
    var churchRole: String? = null,
    // --- BEZIEHUNG ZUR GRUPPE ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    var group: ChurchGroup? = null,
    @Convert(converter = EncryptedBaptismFieldConverter::class)
    @Column(name = "baptism", columnDefinition = "TEXT")
    var baptism: Baptism? = null,
    /**
     * --- AUTH ---
     * Keycloak subject UUID. Populated on registerMember(). null for admin-created members.
     *
     * */
    @Convert(converter = EncryptedKeycloakIdConverter::class)
    @Column(name = "keycloak_id", columnDefinition = "TEXT")
    var keycloakId: String? = null,
    // --- PROFILE ---
    @Convert(converter = EncryptedProfileImageUrlConverter::class)
    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    var profileImageUrl: String? = null,
    // --- DASHBOARD FLAGS ---
    @Column(name = "is_next_group_leader", nullable = false)
    var isNextGroupLeader: Boolean = false,
    @Column(name = "one_on_one_signup_filled", nullable = false)
    var oneOnOneSignupFilled: Boolean = false,
    @Column(name = "email_lookup_hash", length = 64)
    var emailLookupHash: String? = null,
    @Column(name = "keycloak_lookup_hash", length = 64)
    var keycloakLookupHash: String? = null,
    @Column(name = "pii_key_id", length = 50)
    var piiKeyId: String? = null,
    @Column(name = "delete_entry_at")
    var deleteEntryAt: Instant? = null,
    @Column(name = "push_enabled", nullable = false)
    var pushEnabled: Boolean = true,
) : BaseEntity() {
    fun getFullName(): String = "$lastName$firstName" // Korean: no space between surname and given name

    @PrePersist
    @PreUpdate
    fun refreshPiiLookupHashes() {
        emailLookupHash = PiiCryptoContext.lookupHash(email)
        keycloakLookupHash = PiiCryptoContext.lookupHash(keycloakId)
        piiKeyId = PiiCryptoContext.storageKeyId()
    }
}
