package com.hanmaum.dn.app.features.members.repository

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.statistics.api.v1.dto.ChartDataDto
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface MemberRepository : JpaRepository<Member, Long> {
    // 1. Gesamtanzahl (Aktive)
    fun countByDeletedAtIsNull(): Long

    fun findByEmail(email: String?): Member?

    // 2. Einfach: Neue Mitglieder dieses Jahr (Postgres spezifisch: EXTRACT YEAR)
    @Query(
        value = """
            SELECT COUNT(*) FROM members
            WHERE deleted_at IS NULL
            AND EXTRACT(YEAR FROM registration_date) = EXTRACT(YEAR FROM CURRENT_DATE)
        """,
        nativeQuery = true,
    )
    fun countNewMembersYtd(): Long

    // 3. Statistik: Durchschnittsalter
    // Postgres 'AGE' Funktion für Exaktheit
    @Query(
        value = """
        SELECT COALESCE(AVG(EXTRACT(YEAR FROM AGE(birth_date))), 0)    
        FROM members
        WHERE deleted_at IS NULL AND birth_date IS NOT NULL
        """,
        nativeQuery = true,
    )
    fun getAverageAge(): Double

    // 4. Statistik: Städte Verteilung (Top 10)
    // mappen das Ergebnis direkt auf DTO
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

    // 5. Statistik: Altersgruppen
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

    // 6. Geschlechterverteilung
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

    fun findByPublicId(publicId: UUID): Optional<Member>

    fun findAllByFirstNameAndLastName(
        firstName: String,
        lastName: String,
    ): List<Member>

    @Query(
        """
        SELECT m FROM Member m 
        WHERE LOWER(REPLACE(REPLACE(m.lastName, '-', ''), ' ', '')) = LOWER(REPLACE(REPLACE(:lastName, '-', ''), ' ', ''))
        AND LOWER(REPLACE(REPLACE(m.firstName, '-', ''), ' ', '')) = LOWER(REPLACE(REPLACE(:firstName, '-', ''), ' ', ''))
    """,
    )
    fun findSimilarNames(
        @Param("firstName") firstName: String,
        @Param("lastName") lastName: String,
    ): List<Member>
}
