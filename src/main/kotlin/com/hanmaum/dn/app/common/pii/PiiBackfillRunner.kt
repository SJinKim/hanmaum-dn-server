package com.hanmaum.dn.app.common.pii

import com.hanmaum.dn.app.features.groups.repository.MeetingAttendanceRepository
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Component
class PiiBackfillRunner(
    private val properties: PiiProperties,
    private val jdbcTemplate: JdbcTemplate,
    private val memberRepository: MemberRepository,
    private val ministryAssignmentRepository: MinistryAssignmentRepository,
    private val meetingAttendanceRepository: MeetingAttendanceRepository,
    private val transactionTemplate: TransactionTemplate,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(PiiBackfillRunner::class.java)

    override fun run(args: ApplicationArguments) {
        if (PiiCryptoContext.plaintextWriteEnabled()) {
            log.warn("PII backfill skipped mode=LOCAL_PLAINTEXT")
            return
        }

        var remaining = countLegacyRows()
        val sensitiveTextRemaining = countLegacySensitiveText()
        if (remaining == 0L && sensitiveTextRemaining == 0L) {
            log.info("PII backfill check completed remaining=0")
            return
        }
        check(properties.backfillEnabled) {
            "Found plaintext PII while app.pii.backfill-enabled=false."
        }
        check(properties.legacyPlaintextReadEnabled) {
            "PII backfill requires app.pii.legacy-plaintext-read-enabled=true."
        }

        if (remaining > 0) {
            log.warn("PII backfill started remaining={} batchSize={}", remaining, properties.backfillBatchSize)
            var migrated = 0L
            while (remaining > 0) {
                val ids = findLegacyIds()
                check(ids.isNotEmpty()) { "PII backfill stalled with $remaining rows remaining." }

                transactionTemplate.executeWithoutResult {
                    val members = memberRepository.findAllById(ids)
                    members.forEach { it.updatedAt = Instant.now() }
                    memberRepository.saveAllAndFlush(members)
                }
                migrated += ids.size
                remaining = countLegacyRows()
                log.info("PII backfill progress migrated={} remaining={}", migrated, remaining)
            }
            log.info("PII backfill completed migrated={}", migrated)
        }
        backfillSensitiveText()
    }

    private fun findLegacyIds(): List<Long> =
        jdbcTemplate.queryForList(
            """
            SELECT id
            FROM members
            WHERE ${legacyPredicate()}
            ORDER BY id
            LIMIT ?
            """.trimIndent(),
            Long::class.java,
            properties.backfillBatchSize,
        )

    private fun countLegacyRows(): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM members WHERE ${legacyPredicate()}",
            Long::class.java,
        ) ?: 0

    private fun countLegacySensitiveText(): Long =
        listOf(
            "ministry_registrations" to "note",
            "meeting_attendances" to "prayer_request",
        ).sumOf { (table, column) ->
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM $table
                WHERE $column IS NOT NULL
                  AND ${requiresEncryption(column)}
                """.trimIndent(),
                Long::class.java,
            ) ?: 0
        }

    private fun legacyPredicate(): String =
        ENCRYPTED_COLUMNS.joinToString(" OR ") { column ->
            "($column IS NOT NULL AND ${requiresEncryption(column)})"
        }

    private fun backfillSensitiveText() {
        backfillTable(
            table = "ministry_registrations",
            column = "note",
        ) { ids ->
            val rows = ministryAssignmentRepository.findAllById(ids)
            rows.forEach { it.updatedAt = Instant.now() }
            ministryAssignmentRepository.saveAllAndFlush(rows)
        }
        backfillTable(
            table = "meeting_attendances",
            column = "prayer_request",
        ) { ids ->
            val rows = meetingAttendanceRepository.findAllById(ids)
            rows.forEach { it.updatedAt = Instant.now() }
            meetingAttendanceRepository.saveAllAndFlush(rows)
        }
    }

    private fun backfillTable(
        table: String,
        column: String,
        saveBatch: (List<Long>) -> Unit,
    ) {
        while (true) {
            val ids =
                jdbcTemplate.queryForList(
                    """
                    SELECT id FROM $table
                    WHERE $column IS NOT NULL
                      AND ${requiresEncryption(column)}
                    ORDER BY id
                    LIMIT ?
                    """.trimIndent(),
                    Long::class.java,
                    properties.backfillBatchSize,
                )
            if (ids.isEmpty()) {
                return
            }
            transactionTemplate.executeWithoutResult { saveBatch(ids) }
            log.info("Sensitive text backfill progress table={} migrated={}", table, ids.size)
        }
    }

    private fun requiresEncryption(column: String): String =
        """
        (
          LEFT($column, 5) <> 'ENC1${'$'}'
          OR split_part($column, '${'$'}', 2) <> '${PiiCryptoContext.activeKeyId()}'
        )
        """.trimIndent()

    private companion object {
        val ENCRYPTED_COLUMNS =
            listOf(
                "last_name",
                "first_name",
                "discriminator",
                "gender",
                "birth_date",
                "phone_number",
                "email",
                "street",
                "house_number",
                "zip_code",
                "city",
                "registration_date",
                "role",
                "baptism",
                "keycloak_id",
                "profile_image_url",
            )
    }
}
