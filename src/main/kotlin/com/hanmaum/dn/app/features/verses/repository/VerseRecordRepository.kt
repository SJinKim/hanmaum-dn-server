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
     * Every mark in a date span, both kinds at once.
     *
     * One query rather than one per kind: the two streaks can sit on different weeks —
     * recitation follows the chosen verse's week — so the caller passes the span that covers
     * both and splits the rows by kind. Bounded by construction: only the running week ever
     * reaches a client, so this is at most fourteen rows.
     *
     * Served by uq_verse_record, whose leading column is member_id.
     */
    @Query(
        """
        SELECT new com.hanmaum.dn.app.features.verses.repository.VerseRecordMark(r.recordDate, r.kind)
        FROM VerseRecord r
        WHERE r.member.id = :memberId
          AND r.deletedAt IS NULL
          AND r.recordDate BETWEEN :from AND :to
        ORDER BY r.recordDate
        """,
    )
    fun findMarksInRange(
        @Param("memberId") memberId: Long,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<VerseRecordMark>

    /** All-time totals, grouped in the database instead of counted once per kind. */
    @Query(
        """
        SELECT new com.hanmaum.dn.app.features.verses.repository.VerseRecordCount(r.kind, COUNT(r))
        FROM VerseRecord r
        WHERE r.member.id = :memberId AND r.deletedAt IS NULL
        GROUP BY r.kind
        """,
    )
    fun countByKind(
        @Param("memberId") memberId: Long,
    ): List<VerseRecordCount>

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
