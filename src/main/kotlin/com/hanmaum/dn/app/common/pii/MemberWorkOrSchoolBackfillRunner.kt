package com.hanmaum.dn.app.common.pii

import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * HDN-332: moves 소속 from `newcomer_profiles.work_or_school` to `members.work_or_school`.
 *
 * The value is encrypted with its column name as associated data, so SQL cannot copy it.
 * Each row is decrypted under the old context, written to the member (only where the member
 * has none yet) and then cleared at the source in the same transaction, which makes the run
 * idempotent and keeps a value an admin later clears from coming back.
 */
@Component
class MemberWorkOrSchoolBackfillRunner(
    private val jdbcTemplate: JdbcTemplate,
    private val memberRepository: MemberRepository,
    private val transactionTemplate: TransactionTemplate,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(MemberWorkOrSchoolBackfillRunner::class.java)

    override fun run(args: ApplicationArguments) {
        var moved = 0
        var skipped = 0
        while (true) {
            val rows =
                jdbcTemplate.query(
                    """
                    SELECT id, member_id, work_or_school
                    FROM newcomer_profiles
                    WHERE work_or_school IS NOT NULL
                    ORDER BY id
                    LIMIT ?
                    """.trimIndent(),
                    { rs, _ -> Triple(rs.getLong("id"), rs.getLong("member_id"), rs.getString("work_or_school")) },
                    BATCH_SIZE,
                )
            if (rows.isEmpty()) break

            transactionTemplate.executeWithoutResult {
                val members = memberRepository.findAllById(rows.map { it.second }).associateBy { it.id }
                rows.forEach { (_, memberId, stored) ->
                    val value = PiiCryptoContext.decrypt(stored, LEGACY_CONTEXT)?.trim()?.ifBlank { null }
                    val member = members[memberId]
                    if (member != null && member.workOrSchool == null && value != null) {
                        member.workOrSchool = value
                        moved++
                    } else {
                        skipped++
                    }
                }
                memberRepository.saveAllAndFlush(members.values)
                jdbcTemplate.batchUpdate(
                    "UPDATE newcomer_profiles SET work_or_school = NULL WHERE id = ?",
                    rows.map { arrayOf<Any>(it.first) },
                )
            }
        }
        if (moved > 0 || skipped > 0) {
            log.info("Member work_or_school backfill completed moved={} skipped={}", moved, skipped)
        }
    }

    private companion object {
        const val BATCH_SIZE = 200
        const val LEGACY_CONTEXT = "newcomer_profiles.work_or_school"
    }
}
