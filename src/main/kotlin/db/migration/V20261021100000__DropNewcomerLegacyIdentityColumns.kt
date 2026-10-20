package db.migration

import com.hanmaum.dn.app.common.pii.PiiCryptoContext
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.LoggerFactory

/**
 * HDN-339: finishes the HDN-332 move of 신분 and 소속 onto `members` and drops the old columns.
 *
 * 신분 was copied in SQL by V20261020150000. 소속 is encrypted with its column name as associated
 * data, so it is re-encrypted here under the new context before the column goes. A member who
 * already has a value keeps it. Doing the copy inside the migration rather than in an
 * ApplicationRunner guarantees it happens before the drop on every environment.
 *
 * Needs [PiiCryptoContext] initialized; PiiCryptoJpaBootstrapDependency makes the Flyway bean
 * depend on PiiCryptoConfiguration for that reason.
 */
@Suppress("ClassName")
class V20261021100000__DropNewcomerLegacyIdentityColumns : BaseJavaMigration() {
    private val log = LoggerFactory.getLogger(V20261021100000__DropNewcomerLegacyIdentityColumns::class.java)

    override fun migrate(context: Context) {
        val connection = context.connection
        val legacy = mutableListOf<Pair<Long, String>>()
        connection
            .prepareStatement(
                """
                SELECT np.member_id, np.work_or_school
                FROM newcomer_profiles np
                JOIN members m ON m.id = np.member_id
                WHERE np.work_or_school IS NOT NULL AND m.work_or_school IS NULL
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) legacy += rs.getLong("member_id") to rs.getString("work_or_school")
                }
            }

        var moved = 0
        connection
            .prepareStatement("UPDATE members SET work_or_school = ? WHERE id = ? AND work_or_school IS NULL")
            .use { update ->
                legacy.forEach { (memberId, stored) ->
                    val value = PiiCryptoContext.decrypt(stored, LEGACY_CONTEXT)?.trim()?.ifBlank { null } ?: return@forEach
                    update.setString(1, PiiCryptoContext.encrypt(value, MEMBER_CONTEXT))
                    update.setLong(2, memberId)
                    moved += update.executeUpdate()
                }
            }

        connection.createStatement().use {
            it.execute("ALTER TABLE newcomer_profiles DROP COLUMN identity_status, DROP COLUMN work_or_school")
        }
        log.info("Dropped newcomer_profiles identity columns; moved work_or_school for {} members", moved)
    }

    private companion object {
        const val LEGACY_CONTEXT = "newcomer_profiles.work_or_school"
        const val MEMBER_CONTEXT = "members.work_or_school"
    }
}
