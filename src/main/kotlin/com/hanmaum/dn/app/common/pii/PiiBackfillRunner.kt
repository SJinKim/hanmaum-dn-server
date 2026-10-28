package com.hanmaum.dn.app.common.pii

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * Re-encrypts PII that is still stored as plaintext or under a superseded key.
 *
 * Every column is rewritten explicitly: read the stored value, decrypt it under its own crypto
 * context, encrypt it again under the active key, write it back. Re-saving the entity does not
 * work — `Member` is `@DynamicUpdate`, so Hibernate writes only the attributes that changed in
 * memory, and a converted attribute that was loaded and never touched is not one of them. The
 * old runner bumped `updatedAt` hoping the converters would come along; they did not, and the
 * row count never dropped (HDN-360).
 *
 * The crypto context of an encrypted column is always `"<table>.<column>"` — see
 * [PiiAttributeConverters] — so one routine covers the member columns and the free-text ones.
 */
@Component
class PiiBackfillRunner(
    private val properties: PiiProperties,
    private val jdbcTemplate: JdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(PiiBackfillRunner::class.java)

    override fun run(args: ApplicationArguments) {
        if (PiiCryptoContext.plaintextWriteEnabled()) {
            log.warn("PII backfill skipped mode=LOCAL_PLAINTEXT")
            return
        }

        val remaining = countLegacyMembers() + SENSITIVE_TEXT_COLUMNS.sumOf { countLegacy(it.first, it.second) }
        if (remaining == 0L) {
            log.info("PII backfill check completed remaining=0")
            return
        }
        check(properties.backfillEnabled) {
            "Found plaintext PII while app.pii.backfill-enabled=false."
        }
        check(properties.legacyPlaintextReadEnabled) {
            "PII backfill requires app.pii.legacy-plaintext-read-enabled=true."
        }

        log.warn("PII backfill started remaining={} batchSize={}", remaining, properties.backfillBatchSize)
        var migrated = 0L
        ENCRYPTED_COLUMNS.forEach { (table, column) ->
            migrated += backfillColumn(table, column)
        }
        log.info("PII backfill completed migrated={}", migrated)
    }

    /**
     * Rewrites one column batch by batch. After each batch the rows just written are re-checked:
     * one that still looks legacy means the write did not take, and the runner aborts instead of
     * spinning forever over the same ids.
     */
    private fun backfillColumn(
        table: String,
        column: String,
    ): Long {
        var migrated = 0L
        while (true) {
            val ids = rewriteBatch(table, column)
            if (ids.isEmpty()) {
                return migrated
            }
            val stalled = countLegacy(table, column, ids)
            check(stalled == 0L) {
                "PII backfill stalled table=$table column=$column rows=$stalled"
            }
            migrated += ids.size
            log.info("PII backfill progress table={} column={} migrated={}", table, column, migrated)
        }
    }

    /**
     * Reads and rewrites one batch in a single transaction, with the rows locked. Reading first and
     * writing later would encrypt a stale snapshot over a value another writer committed in between;
     * under `FOR UPDATE` a concurrent writer either finishes before the read (the runner sees its
     * value) or waits until the batch is committed. Returns the ids written.
     */
    private fun rewriteBatch(
        table: String,
        column: String,
    ): List<Long> =
        transactionTemplate.execute {
            val rows = findLegacyRows(table, column)
            rows.forEach { (id, stored) -> rewriteRow(table, column, id, stored) }
            rows.map { it.first }
        } ?: emptyList()

    private fun findLegacyRows(
        table: String,
        column: String,
    ): List<Pair<Long, String>> =
        jdbcTemplate.query(
            """
            SELECT id, $column AS legacy_value
            FROM $table
            WHERE $column IS NOT NULL
              AND ${requiresEncryption(column)}
            ORDER BY id
            LIMIT ?
            FOR UPDATE
            """.trimIndent(),
            { rs, _ -> rs.getLong("id") to rs.getString("legacy_value") },
            properties.backfillBatchSize,
        )

    /**
     * `members` also carries the key id the row was written with and the lookup hashes derived
     * from the plaintext; the entity keeps them in sync via `@PreUpdate`, so a direct write has
     * to do the same. The hashes do not depend on the storage key, but a row written before
     * encryption may not have them at all.
     */
    private fun rewriteRow(
        table: String,
        column: String,
        id: Long,
        stored: String,
    ) {
        val context = "$table.$column"
        val plaintext =
            try {
                PiiCryptoContext.decrypt(stored, context)
            } catch (failure: RuntimeException) {
                throw IllegalStateException("PII backfill cannot decrypt table=$table column=$column id=$id", failure)
            }
        val encrypted =
            checkNotNull(PiiCryptoContext.encrypt(plaintext, context)) {
                "PII backfill produced no value table=$table column=$column id=$id"
            }

        val assignments = mutableListOf("$column = ?")
        val arguments = mutableListOf<Any?>(encrypted)
        if (table == MEMBERS_TABLE) {
            assignments += "pii_key_id = ?"
            arguments += PiiCryptoContext.storageKeyId()
            LOOKUP_HASH_COLUMNS[column]?.let { hashColumn ->
                assignments += "$hashColumn = ?"
                arguments += PiiCryptoContext.lookupHash(plaintext)
            }
        }
        arguments += id
        jdbcTemplate.update(
            "UPDATE $table SET ${assignments.joinToString(", ")} WHERE id = ?",
            *arguments.toTypedArray(),
        )
    }

    private fun countLegacyMembers(): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM $MEMBERS_TABLE WHERE ${legacyMemberPredicate()}",
            Long::class.java,
        ) ?: 0

    private fun countLegacy(
        table: String,
        column: String,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM $table
            WHERE $column IS NOT NULL
              AND ${requiresEncryption(column)}
            """.trimIndent(),
            Long::class.java,
        ) ?: 0

    private fun countLegacy(
        table: String,
        column: String,
        ids: List<Long>,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM $table
            WHERE id IN (${ids.joinToString(", ") { "?" }})
              AND $column IS NOT NULL
              AND ${requiresEncryption(column)}
            """.trimIndent(),
            Long::class.java,
            *ids.toTypedArray(),
        ) ?: 0

    private fun legacyMemberPredicate(): String =
        MEMBER_COLUMNS.joinToString(" OR ") { column ->
            "($column IS NOT NULL AND ${requiresEncryption(column)})"
        }

    private fun requiresEncryption(column: String): String =
        """
        (
          LEFT($column, 5) <> 'ENC1${'$'}'
          OR split_part($column, '${'$'}', 2) <> '${PiiCryptoContext.activeKeyId()}'
        )
        """.trimIndent()

    private companion object {
        const val MEMBERS_TABLE = "members"

        val MEMBER_COLUMNS =
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
                "occupation",
                "work_or_school",
                "kakao_id",
                "registration_date",
                "baptism",
                "keycloak_id",
                "profile_image_url",
            )

        val SENSITIVE_TEXT_COLUMNS =
            listOf(
                "ministry_registrations" to "note",
                "meeting_attendances" to "prayer_request",
            )

        val ENCRYPTED_COLUMNS = MEMBER_COLUMNS.map { MEMBERS_TABLE to it } + SENSITIVE_TEXT_COLUMNS

        /** Lookup hashes `members` keeps beside the encrypted value, by source column. */
        val LOOKUP_HASH_COLUMNS =
            mapOf(
                "email" to "email_lookup_hash",
                "keycloak_id" to "keycloak_lookup_hash",
            )
    }
}
