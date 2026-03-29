package com.hanmaum.dn.app.features.members.repository

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.statistics.api.v1.dto.ChartDataDto
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface MemberRepository : JpaRepository<Member, Long> {
    // ─── Soft-delete aware lookups ────────────────────────────────────────────

    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<Member>

    fun findByEmailAndDeletedAtIsNull(email: String): Member?

    fun findByKeycloakIdAndDeletedAtIsNull(keycloakId: String): Member?

    /**
     * Paginated list of active (non-deleted) members.
     * Optional [search] filters on lastName OR firstName OR email (case-insensitive).
     */
    @Query(
        """
        SELECT m FROM Member m
        WHERE m.deletedAt IS NULL
          AND (:status IS NULL OR m.memberStatus = :status)
          AND (
            :search IS NULL
            OR LOWER(m.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(m.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(m.email)     LIKE LOWER(CONCAT('%', :search, '%'))
          )
        ORDER BY m.lastName ASC, m.firstName ASC
        """,
    )
    fun findActiveMembers(
        @Param("search") search: String?,
        @Param("status") status: MemberStatus?,
        pageable: Pageable,
    ): Page<Member>

    // ─── Name de-duplication (register flow) ─────────────────────────────────

    @Query(
        """
        SELECT m FROM Member m
        WHERE LOWER(REPLACE(REPLACE(m.lastName,  '-', ''), ' ', ''))
                = LOWER(REPLACE(REPLACE(:lastName,  '-', ''), ' ', ''))
          AND LOWER(REPLACE(REPLACE(m.firstName, '-', ''), ' ', ''))
                = LOWER(REPLACE(REPLACE(:firstName, '-', ''), ' ', ''))
        """,
    )
    fun findSimilarNames(
        @Param("firstName") firstName: String,
        @Param("lastName") lastName: String,
    ): List<Member>

    // ─── Statistics queries ───────────────────────────────────────────────────

    fun countByDeletedAtIsNull(): Long

    @Query(
        value = """
            SELECT COUNT(*) FROM members
            WHERE deleted_at IS NULL
              AND EXTRACT(YEAR FROM registration_date) = EXTRACT(YEAR FROM CURRENT_DATE)
        """,
        nativeQuery = true,
    )
    fun countNewMembersYtd(): Long

    @Query(
        value = """
            SELECT COALESCE(AVG(EXTRACT(YEAR FROM AGE(birth_date))), 0)
            FROM members
            WHERE deleted_at IS NULL AND birth_date IS NOT NULL
        """,
        nativeQuery = true,
    )
    fun getAverageAge(): Double

    @Query(
        """
        SELECT new com.hanmaum.dn.app.features.statistics.api.v1.dto.ChartDataDto(m.city, COUNT(m))
        FROM Member m
        WHERE m.deletedAt IS NULL AND m.city IS NOT NULL
        GROUP BY m.city
        ORDER BY COUNT(m) DESC
        """,
    )
    fun getCityDistribution(): List<ChartDataDto>

    @Query(
        value = """
        SELECT
            CASE
                WHEN EXTRACT(YEAR FROM AGE(birth_date)) BETWEEN 16 AND 19 THEN '10대'
                WHEN EXTRACT(YEAR FROM AGE(birth_date)) BETWEEN 20 AND 29 THEN '20대'
                WHEN EXTRACT(YEAR FROM AGE(birth_date)) BETWEEN 30 AND 39 THEN '30대'
                WHEN EXTRACT(YEAR FROM AGE(birth_date)) BETWEEN 40 AND 60 THEN '40대+'
            END as label,
            COUNT(*) as value
        FROM members
        WHERE deleted_at IS NULL AND birth_date IS NOT NULL
        GROUP BY label
        ORDER BY label
        """,
        nativeQuery = true,
    )
    fun getAgeGroupsNative(): List<Array<Any>>

    @Query(
        """
        SELECT new com.hanmaum.dn.app.features.statistics.api.v1.dto.ChartDataDto(
            CASE WHEN m.gender = 'M' THEN '형제'
                 WHEN m.gender = 'F' THEN '자매'
                 ELSE 'Unbekannt'
            END,
            COUNT(m)
        )
        FROM Member m
        WHERE m.deletedAt IS NULL
        GROUP BY m.gender
        """,
    )
    fun getGenderDistribution(): List<ChartDataDto>

    // ─── Legacy — kept for existing callers; prefer the soft-delete-aware variants above ───

    fun findByEmail(email: String?): Member?

    fun findByPublicId(publicId: UUID): Optional<Member>

    fun findAllByFirstNameAndLastName(
        firstName: String,
        lastName: String,
    ): List<Member>
}
