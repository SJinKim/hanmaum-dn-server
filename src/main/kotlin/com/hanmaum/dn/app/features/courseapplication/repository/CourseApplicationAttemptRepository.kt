package com.hanmaum.dn.app.features.courseapplication.repository

import com.hanmaum.dn.app.features.courseapplication.domain.CourseApplicationAttempt
import com.hanmaum.dn.app.features.courseapplication.domain.CourseApplicationAttemptStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CourseApplicationAttemptRepository : JpaRepository<CourseApplicationAttempt, Long> {
    /**
     * The live attempt for one member and external course, keyed exactly like
     * uq_course_application_attempt_member_course.
     */
    @Query(
        """
        SELECT a FROM CourseApplicationAttempt a
        WHERE a.member.id = :memberId
          AND a.externalCourseId = :externalCourseId
          AND a.status <> com.hanmaum.dn.app.features.courseapplication.domain.CourseApplicationAttemptStatus.CANCELLED
          AND a.deletedAt IS NULL
        """,
    )
    fun findLive(
        @Param("memberId") memberId: Long,
        @Param("externalCourseId") externalCourseId: Int,
    ): CourseApplicationAttempt?

    /** A member's attempts in [statuses], newest first, with the training fetched. */
    @Query(
        """
        SELECT a FROM CourseApplicationAttempt a
        JOIN FETCH a.training
        WHERE a.member.id = :memberId
          AND a.status IN :statuses
          AND a.deletedAt IS NULL
        ORDER BY a.createdAt DESC
        """,
    )
    fun findByMember(
        @Param("memberId") memberId: Long,
        @Param("statuses") statuses: Collection<CourseApplicationAttemptStatus>,
    ): List<CourseApplicationAttempt>
}
