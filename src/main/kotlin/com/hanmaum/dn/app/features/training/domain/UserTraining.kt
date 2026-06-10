package com.hanmaum.dn.app.features.training.domain

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
import java.time.LocalDate

@Entity
@Table(
    name = "user_training",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_user_training_member_training",
            columnNames = ["user_id", "training_id"],
        ),
    ],
)
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
) : BaseEntity()
