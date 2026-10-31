package db.migration

import com.hanmaum.dn.app.common.pii.ABSENCE_CHANGE_ACTOR_CONTEXT
import com.hanmaum.dn.app.common.pii.ABSENCE_CHANGE_MEMO_CONTEXT
import com.hanmaum.dn.app.common.pii.ABSENCE_FOLLOWUP_CONTACTED_BY_CONTEXT
import com.hanmaum.dn.app.common.pii.ABSENCE_FOLLOWUP_MEMO_CONTEXT
import com.hanmaum.dn.app.common.pii.PiiCryptoContext
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Timestamp
import java.util.UUID

/**
 * HDN-379: records the current contact status and memo of every existing long-absence phase as
 * its first history entry, after V20261031120000 created attendance_absence_changes.
 *
 * Only what is known is copied. A contacted phase gets a CONTACT_CHANGED snapshot with the stored
 * actor and time; a memo gets a MEMO_CHANGED snapshot without actor or time, because HDN-375 never
 * stored who wrote it. contacted = false records nothing. The snapshot is not a reconstructed
 * edit history, and created_at is the migration time, not a claimed change time.
 *
 * Both values are encrypted with their column name as associated data, so they are decrypted and
 * re-encrypted under the new column context, which SQL cannot do. A value that cannot be decrypted
 * fails the migration (rolled back, startup blocked) instead of being skipped: losing a memo
 * silently would be worse. The message names the row id only.
 *
 * Rows are read in id batches; the unique (followup_id, revision, kind) makes a rerun a no-op.
 * Kotlin rather than SQL because it needs [PiiCryptoContext]; PiiCryptoJpaBootstrapDependency
 * makes the Flyway bean depend on PiiCryptoConfiguration so the keyring is ready when this runs.
 */
@Suppress("ClassName")
class V20261031120100__SnapshotAbsenceFollowupChanges : BaseJavaMigration() {
    private val log = LoggerFactory.getLogger(V20261031120100__SnapshotAbsenceFollowupChanges::class.java)

    override fun migrate(context: Context) {
        val connection = context.connection
        var afterId = 0L
        var contacts = 0
        var memos = 0
        while (true) {
            val batch = readBatch(connection, afterId)
            if (batch.isEmpty()) break
            connection
                .prepareStatement(
                    """
                    INSERT INTO attendance_absence_changes
                        (public_id, followup_id, revision, kind, origin, occurred_at, actor_subject, contacted, memo, pii_key_id)
                    VALUES (?, ?, 0, ?, 'LEGACY_SNAPSHOT', ?, ?, ?, ?, ?)
                    ON CONFLICT (followup_id, revision, kind) DO NOTHING
                    """.trimIndent(),
                ).use { insert ->
                    batch.forEach { row ->
                        if (row.contacted) {
                            insert.setObject(1, UUID.randomUUID())
                            insert.setLong(2, row.id)
                            insert.setString(3, "CONTACT_CHANGED")
                            insert.setTimestamp(4, row.contactedAt)
                            insert.setString(
                                5,
                                reencrypt(row.id, row.contactedBy, ABSENCE_FOLLOWUP_CONTACTED_BY_CONTEXT, ABSENCE_CHANGE_ACTOR_CONTEXT),
                            )
                            insert.setObject(6, true)
                            insert.setString(7, null)
                            insert.setString(8, PiiCryptoContext.storageKeyId())
                            insert.addBatch()
                            contacts++
                        }
                        if (row.memo != null) {
                            insert.setObject(1, UUID.randomUUID())
                            insert.setLong(2, row.id)
                            insert.setString(3, "MEMO_CHANGED")
                            insert.setTimestamp(4, null)
                            insert.setString(5, null)
                            insert.setObject(6, null)
                            insert.setString(7, reencrypt(row.id, row.memo, ABSENCE_FOLLOWUP_MEMO_CONTEXT, ABSENCE_CHANGE_MEMO_CONTEXT))
                            insert.setString(8, PiiCryptoContext.storageKeyId())
                            insert.addBatch()
                            memos++
                        }
                    }
                    insert.executeBatch()
                }
            afterId = batch.last().id
        }
        log.info("Snapshotted long-absence follow-ups contacts={} memos={}", contacts, memos)
    }

    private fun readBatch(
        connection: Connection,
        afterId: Long,
    ): List<LegacyPhase> =
        connection
            .prepareStatement(
                """
                SELECT id, contacted, contacted_by, contacted_at, memo
                FROM attendance_absence_followups
                WHERE id > ? AND (contacted OR memo IS NOT NULL)
                ORDER BY id
                LIMIT $BATCH_SIZE
                """.trimIndent(),
            ).use { statement ->
                statement.setLong(1, afterId)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                LegacyPhase(
                                    id = rs.getLong("id"),
                                    contacted = rs.getBoolean("contacted"),
                                    contactedBy = rs.getString("contacted_by"),
                                    contactedAt = rs.getTimestamp("contacted_at"),
                                    memo = rs.getString("memo"),
                                ),
                            )
                        }
                    }
                }
            }

    private fun reencrypt(
        id: Long,
        stored: String?,
        from: String,
        to: String,
    ): String {
        val plaintext =
            try {
                PiiCryptoContext.decrypt(stored, from)
            } catch (failure: RuntimeException) {
                // The cause stays out: a crypto failure message may quote the stored value.
                throw IllegalStateException("Cannot decrypt long-absence follow-up id=$id context=$from")
            }
        checkNotNull(plaintext) { "Long-absence follow-up id=$id has no value for context=$from" }
        return checkNotNull(PiiCryptoContext.encrypt(plaintext, to)) { "Encryption produced no value for follow-up id=$id" }
    }

    private class LegacyPhase(
        val id: Long,
        val contacted: Boolean,
        val contactedBy: String?,
        val contactedAt: Timestamp?,
        val memo: String?,
    ) {
        override fun toString(): String = "LegacyPhase(id=$id, contacted=$contacted)"
    }

    private companion object {
        const val BATCH_SIZE = 500
    }
}
