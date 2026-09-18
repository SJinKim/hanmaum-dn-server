package com.hanmaum.dn.app.features.newcomers.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.common.pii.EncryptedNewcomerAdditionalNotesConverter
import com.hanmaum.dn.app.common.pii.EncryptedNewcomerAssignmentReasonConverter
import com.hanmaum.dn.app.common.pii.EncryptedNewcomerEnglishNameConverter
import com.hanmaum.dn.app.common.pii.EncryptedNewcomerFirstVisitDateConverter
import com.hanmaum.dn.app.common.pii.EncryptedNewcomerKakaoIdConverter
import com.hanmaum.dn.app.common.pii.EncryptedNewcomerOverallNotesConverter
import com.hanmaum.dn.app.common.pii.EncryptedNewcomerPreviousChurchConverter
import com.hanmaum.dn.app.common.pii.EncryptedNewcomerVisitMotivesConverter
import com.hanmaum.dn.app.common.pii.EncryptedNewcomerWorkOrSchoolConverter
import com.hanmaum.dn.app.common.pii.PiiCryptoContext
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDate

enum class NewcomerLifecycle { SUBMITTED, IN_CARE, GRADUATED, ARCHIVED }

enum class NewcomerIdentityStatus {
    EMPLOYEE,
    UNIVERSITY_STUDENT,
    EXAM_PREPARATION,
    WORKING_HOLIDAY,
    EXCHANGE_STUDENT,
    SELF_EMPLOYED,
    EXPATRIATE,
    JOB_SEEKING,
}

enum class PostAssignmentAttendance { REGULAR, OCCASIONAL, WORSHIP_ONLY, ABSENT_OVER_MONTH, CHANGED_CHURCH, RETURNED_OR_MOVED }

enum class ChurchExperience { FIRST_TIME, CHILDHOOD_FEW_TIMES, IRREGULAR, REGULAR }

@Entity
@Table(name = "newcomer_profiles")
class NewcomerProfile(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    val member: Member,
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    var lifecycleStatus: NewcomerLifecycle = NewcomerLifecycle.SUBMITTED,
    @Column(name = "intake_round")
    var intakeRound: Int? = null,
    @Column(name = "has_visited", nullable = false)
    var hasVisited: Boolean = false,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caregiver_member_id")
    var caregiver: Member? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "identity_status", length = 30)
    var identityStatus: NewcomerIdentityStatus? = null,
    @Convert(converter = EncryptedNewcomerWorkOrSchoolConverter::class)
    @Column(name = "work_or_school", columnDefinition = "TEXT")
    var workOrSchool: String? = null,
    @Convert(converter = EncryptedNewcomerFirstVisitDateConverter::class)
    @Column(name = "first_visit_date", columnDefinition = "TEXT")
    var firstVisitDate: LocalDate? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_group_id")
    var assignedGroup: ChurchGroup? = null,
    @Convert(converter = EncryptedNewcomerAssignmentReasonConverter::class)
    @Column(name = "assignment_reason", columnDefinition = "TEXT")
    var assignmentReason: String? = null,
    @Convert(converter = EncryptedNewcomerOverallNotesConverter::class)
    @Column(name = "overall_notes", columnDefinition = "TEXT")
    var overallNotes: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "post_assignment_attendance", length = 30)
    var postAssignmentAttendance: PostAssignmentAttendance? = null,
    @Convert(converter = EncryptedNewcomerEnglishNameConverter::class)
    @Column(name = "english_name", columnDefinition = "TEXT")
    var englishName: String? = null,
    @Convert(converter = EncryptedNewcomerKakaoIdConverter::class)
    @Column(name = "kakao_id", columnDefinition = "TEXT")
    var kakaoId: String? = null,
    @Convert(converter = EncryptedNewcomerPreviousChurchConverter::class)
    @Column(name = "previous_church", columnDefinition = "TEXT")
    var previousChurch: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "church_experience", length = 30)
    var churchExperience: ChurchExperience? = null,
    @Convert(converter = EncryptedNewcomerVisitMotivesConverter::class)
    @Column(name = "visit_motives", columnDefinition = "TEXT")
    var visitMotives: String? = null,
    @Convert(converter = EncryptedNewcomerAdditionalNotesConverter::class)
    @Column(name = "additional_notes", columnDefinition = "TEXT")
    var additionalNotes: String? = null,
    @Column(name = "pii_key_id", length = 50)
    var piiKeyId: String? = null,
) : BaseEntity() {
    @Version
    @Column(nullable = false)
    var version: Long = 0

    @PrePersist
    @PreUpdate
    fun refreshPiiKeyId() {
        piiKeyId = PiiCryptoContext.storageKeyId()
    }

    override fun toString(): String = "NewcomerProfile(id=$id, publicId=$publicId, lifecycleStatus=$lifecycleStatus, version=$version)"
}
