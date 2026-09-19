package com.hanmaum.dn.app.features.newcomers.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.common.pii.EncryptedNewcomerGraduationReasonConverter
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDate

@Entity
@Table(name = "newcomer_graduations")
class NewcomerGraduation(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "newcomer_profile_id", nullable = false, unique = true)
    val newcomerProfile: NewcomerProfile,
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    val member: Member,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: ChurchGroup,
    @Column(name = "cohort_number", nullable = false)
    val cohortNumber: Int,
    @Column(name = "cohort_label", nullable = false, length = 30)
    val cohortLabel: String,
    @Column(name = "graduated_on", nullable = false)
    val graduatedOn: LocalDate,
    @Convert(converter = EncryptedNewcomerGraduationReasonConverter::class)
    @Column(name = "assignment_reason", columnDefinition = "TEXT")
    val assignmentReason: String?,
    @Column(name = "graduated_by", nullable = false, length = 64)
    val graduatedBy: String,
) : BaseEntity() {
    @Version
    @Column(nullable = false)
    var version: Long = 0
}
