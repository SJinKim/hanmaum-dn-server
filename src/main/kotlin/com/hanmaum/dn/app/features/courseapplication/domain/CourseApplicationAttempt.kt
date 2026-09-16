package com.hanmaum.dn.app.features.courseapplication.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.training.domain.Training
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

/**
 * Where an application to application.hanmaum.de stands, from the DN side.
 *
 * [PENDING] is written before the external call. It means "the external system may or may
 * not have this application" — a response can be lost after the insert on their side.
 */
enum class CourseApplicationAttemptStatus {
    PENDING,
    CREATED,
    CANCELLED,
}

/**
 * One member's application to one external course, as far as this server knows it.
 *
 * Exists for idempotency: [clientApplicationId] is minted once and reused by every retry, so
 * the external API can recognise a repeat instead of creating a second application. Carries
 * no PII — the applicant's details go to the external API only.
 *
 * The one-live-attempt-per-course rule is the partial index
 * uq_course_application_attempt_member_course, not declared here for the same reason as on
 * UserTraining: JPA cannot express a partial index.
 */
@Entity
@Table(name = "course_application_attempts")
class CourseApplicationAttempt(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id", nullable = false)
    val training: Training,
    @Column(name = "external_course_id", nullable = false)
    val externalCourseId: Int,
    /** The course name as published when the member applied. Not PII. */
    @Column(name = "external_course_name", nullable = false, length = 255)
    val externalCourseName: String,
    @Column(name = "client_application_id", nullable = false, unique = true, updatable = false)
    val clientApplicationId: UUID = UUID.randomUUID(),
    @Column(name = "external_application_id")
    var externalApplicationId: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: CourseApplicationAttemptStatus = CourseApplicationAttemptStatus.PENDING,
) : BaseEntity() {
    fun markCreated(externalId: Long) {
        externalApplicationId = externalId
        status = CourseApplicationAttemptStatus.CREATED
    }

    /**
     * Cancelled on the external side. No longer live, so uq_course_application_attempt_member_course
     * lets the member apply to the course again under a new [clientApplicationId].
     */
    fun markCancelled(externalId: Long) {
        externalApplicationId = externalId
        status = CourseApplicationAttemptStatus.CANCELLED
    }

    override fun toString(): String = "CourseApplicationAttempt(id=$id, externalCourseId=$externalCourseId, status=$status)"
}
