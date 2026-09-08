package com.hanmaum.dn.app.features.verses.repository

import com.hanmaum.dn.app.features.verses.domain.VerseRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface VerseRecordRepository : JpaRepository<VerseRecord, Long> {
    /**
     * The marks of one kind inside one week. Only seven days ever reach the client, so this
     * is bounded by construction — a member with years of history still transfers a week.
     */
    @Query(
        """
        SELECT r.recordDate FROM VerseRecord r
        WHERE r.member.id = :memberId
          AND r.kind = :kind
          AND r.deletedAt IS NULL
          AND r.recordDate BETWEEN :from AND :to
        ORDER BY r.recordDate
        """,
    )
    fun findDatesInRange(
        @Param("memberId") memberId: Long,
        @Param("kind") kind: String,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<LocalDate>

    @Query(
        """
        SELECT COUNT(r) FROM VerseRecord r
        WHERE r.member.id = :memberId AND r.kind = :kind AND r.deletedAt IS NULL
        """,
    )
    fun countForMember(
        @Param("memberId") memberId: Long,
        @Param("kind") kind: String,
    ): Long

    /**
     * Atomically records one mark. The unique constraint is the duplicate guard, so two
     * taps racing each other cannot both insert — the same pattern attendance_logs uses.
     * Returns 0 when the day was already marked.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO verse_records (public_id, member_id, record_date, kind, created_at)
            VALUES (:publicId, :memberId, :recordDate, :kind, CURRENT_TIMESTAMP)
            ON CONFLICT ON CONSTRAINT uq_verse_record DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("publicId") publicId: UUID,
        @Param("memberId") memberId: Long,
        @Param("recordDate") recordDate: LocalDate,
        @Param("kind") kind: String,
    ): Int
}
