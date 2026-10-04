package com.hanmaum.dn.app.features.newcomers.domain

import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.common.pii.EncryptedNewcomerVisitFirstNameConverter
import com.hanmaum.dn.app.common.pii.EncryptedNewcomerVisitGenderConverter
import com.hanmaum.dn.app.common.pii.EncryptedNewcomerVisitLastNameConverter
import com.hanmaum.dn.app.common.pii.EncryptedNewcomerVisitNoteConverter
import com.hanmaum.dn.app.common.pii.PiiCryptoContext
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
import java.time.LocalDate

enum class NewcomerVisitType { FIRST, REVISIT }

/** 알게 된 경로: how the visitor heard of the church. Drives the 광고 evaluation. */
enum class NewcomerVisitSource { FRIEND_FAMILY, ADVERTISEMENT, SOCIAL_MEDIA, WEBSITE, WALK_IN, OTHER }

/**
 * One visitor captured on the day (방문 기록, #261).
 *
 * Deliberately not a [NewcomerProfile]: most visitors never fill in the 새가족 form. The row
 * stays as historical data; [newcomerProfile] links it once the visitor registers, which is
 * what the funnel 방문 → 새가족 등록 → 등반 counts.
 */
@Entity
@Table(name = "newcomer_visits")
class NewcomerVisit(
    @Column(name = "visit_date", nullable = false)
    var visitDate: LocalDate,
    @Convert(converter = EncryptedNewcomerVisitLastNameConverter::class)
    @Column(name = "last_name", nullable = false, columnDefinition = "TEXT")
    var lastName: String,
    @Convert(converter = EncryptedNewcomerVisitFirstNameConverter::class)
    @Column(name = "first_name", nullable = false, columnDefinition = "TEXT")
    var firstName: String,
    @Convert(converter = EncryptedNewcomerVisitGenderConverter::class)
    @Column(name = "gender", columnDefinition = "TEXT")
    var gender: Gender? = null,
    @Column(name = "birth_year")
    var birthYear: Int? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "visit_type", nullable = false, length = 20)
    var visitType: NewcomerVisitType = NewcomerVisitType.FIRST,
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 30)
    var source: NewcomerVisitSource? = null,
    @Convert(converter = EncryptedNewcomerVisitNoteConverter::class)
    @Column(name = "note", columnDefinition = "TEXT")
    var note: String? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "newcomer_profile_id")
    var newcomerProfile: NewcomerProfile? = null,
    @Column(name = "pii_key_id", length = 50)
    var piiKeyId: String? = null,
) : BaseEntity() {
    @PrePersist
    @PreUpdate
    fun refreshPiiKeyId() {
        piiKeyId = PiiCryptoContext.storageKeyId()
    }

    override fun toString(): String = "NewcomerVisit(id=$id, publicId=$publicId, visitDate=$visitDate, visitType=$visitType)"
}
