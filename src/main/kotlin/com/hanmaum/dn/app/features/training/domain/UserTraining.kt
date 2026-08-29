package com.hanmaum.dn.app.features.training.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.common.pii.EncryptedMentorNameConverter
import com.hanmaum.dn.app.common.pii.EncryptedUserTrainingNoteConverter
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * A member's participation in one course.
 *
 * Uniqueness is enforced by the partial index uq_user_training_member_training_variant
 * on (user_id, training_id, COALESCE(variant, '')) WHERE deleted_at IS NULL. It is not
 * declared here: JPA cannot express a partial index or the COALESCE, and Hibernate's
 * schema validation does not check unique constraints, so a @Table declaration would
 * silently drift from the database.
 */
@Entity
@Table(name = "user_training")
class UserTraining(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var member: Member,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id", nullable = false)
    var training: Training,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: TrainingStatus = TrainingStatus.IN_PROGRESS,
    /** First-of-month DATE (YY/MM granularity on the form); null while in progress. */
    @Column(name = "completed_at")
    var completedAt: LocalDate? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    var cohort: TrainingCohort? = null,
    @Column(name = "applied_on")
    var appliedOn: LocalDate? = null,
    @Column(name = "started_on")
    var startedOn: LocalDate? = null,
    /** Set only for courses taken more than once, e.g. 성경개관 구약 / 신약. */
    @Enumerated(EnumType.STRING)
    @Column(name = "variant", length = 30)
    var variant: TrainingVariant? = null,
    /** Resolved mentor (양육자); null when the raw name could not be matched. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_member_id")
    var mentor: Member? = null,
    /** Mentor name as written in the source, honorifics included. Encrypted: it is a name. */
    @Convert(converter = EncryptedMentorNameConverter::class)
    @Column(name = "mentor_name_raw", columnDefinition = "TEXT")
    var mentorNameRaw: String? = null,
    /** Free text an admin wrote about this participation; may name another member. */
    @Convert(converter = EncryptedUserTrainingNoteConverter::class)
    @Column(name = "note", columnDefinition = "TEXT")
    var note: String? = null,
) : BaseEntity()
