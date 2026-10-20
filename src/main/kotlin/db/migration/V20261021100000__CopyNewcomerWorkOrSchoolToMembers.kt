package db.migration

import com.hanmaum.dn.app.common.pii.MEMBER_WORK_OR_SCHOOL_CONTEXT
import com.hanmaum.dn.app.common.pii.PiiCryptoContext
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.LoggerFactory

/**
 * HDN-339: copies 소속 from `newcomer_profiles` onto `members`, finishing the HDN-332 move.
 *
 * 신분 was copied in SQL by V20261020150000. 소속 is encrypted with its column name as associated
 * data, so it has to be re-encrypted under the member context, which SQL cannot do. Doing it in a
 * migration rather than an ApplicationRunner guarantees it runs before V20261021110000 drops the
 * old columns.
 *
 * A member who already has a value keeps it, and soft-deleted profiles and members are left alone
 * so a deliberately removed 소속 does not come back. A row that cannot be decrypted is logged and
 * skipped rather than failing the migration, because a failed migration blocks startup.
 *
 * Kotlin rather than SQL because it needs [PiiCryptoContext]; PiiCryptoJpaBootstrapDependency
 * makes the Flyway bean depend on PiiCryptoConfiguration so the keyring is ready when this runs.
 */
@Suppress("ClassName")
class V20261021100000__CopyNewcomerWorkOrSchoolToMembers : BaseJavaMigration() {
    private val log = LoggerFactory.getLogger(V20261021100000__CopyNewcomerWorkOrSchoolToMembers::class.java)

    override fun migrate(context: Context) {
        val connection = context.connection
        val legacy = mutableListOf<LegacyValue>()
        connection
            .prepareStatement(
                """
                SELECT np.id, np.member_id, np.work_or_school
                FROM newcomer_profiles np
                JOIN members m ON m.id = np.member_id
                WHERE np.work_or_school IS NOT NULL
                  AND m.work_or_school IS NULL
                  AND np.deleted_at IS NULL
                  AND m.deleted_at IS NULL
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        legacy += LegacyValue(rs.getLong("id"), rs.getLong("member_id"), rs.getString("work_or_school"))
                    }
                }
            }

        var undecryptable = 0
        val moved =
            connection
                .prepareStatement(
                    """
                    UPDATE members SET work_or_school = ?, pii_key_id = ?, updated_at = now()
                    WHERE id = ? AND work_or_school IS NULL
                    """.trimIndent(),
                ).use { update ->
                    legacy.forEach { row ->
                        val value =
                            runCatching { PiiCryptoContext.decrypt(row.stored, LEGACY_CONTEXT) }
                                .onFailure {
                                    undecryptable++
                                    log.warn("Skipping undecryptable work_or_school on newcomer profile {}", row.profileId)
                                }.getOrNull()
                                ?.trim()
                                ?.ifBlank { null } ?: return@forEach
                        update.setString(1, PiiCryptoContext.encrypt(value, MEMBER_WORK_OR_SCHOOL_CONTEXT))
                        update.setString(2, PiiCryptoContext.storageKeyId())
                        update.setLong(3, row.memberId)
                        update.addBatch()
                    }
                    update.executeBatch().sum()
                }

        log.info("Copied work_or_school onto {} members; skipped {} undecryptable rows", moved, undecryptable)
    }

    private class LegacyValue(
        val profileId: Long,
        val memberId: Long,
        val stored: String,
    )

    companion object {
        const val LEGACY_CONTEXT = "newcomer_profiles.work_or_school"
    }
}
