package db.migration

import com.hanmaum.dn.app.common.pii.MEMBER_KAKAO_ID_CONTEXT
import com.hanmaum.dn.app.common.pii.PiiCryptoContext
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.LoggerFactory

/**
 * HDN-360: copies 카카오 아이디 from `newcomer_profiles` onto `members`, finishing the move
 * V20261025120000 started.
 *
 * kakao_id is encrypted with its column name as associated data, so it has to be re-encrypted
 * under the member context, which SQL cannot do. Doing it in a migration rather than an
 * ApplicationRunner guarantees it runs before V20261025120200 drops the old column.
 *
 * A member who already has a value keeps it, and soft-deleted profiles and members are left alone
 * so a deliberately removed handle does not come back. A row that cannot be decrypted is logged and
 * skipped rather than failing the migration, because a failed migration blocks startup.
 *
 * Kotlin rather than SQL because it needs [PiiCryptoContext]; PiiCryptoJpaBootstrapDependency
 * makes the Flyway bean depend on PiiCryptoConfiguration so the keyring is ready when this runs.
 */
@Suppress("ClassName")
class V20261025120100__CopyNewcomerKakaoIdToMembers : BaseJavaMigration() {
    private val log = LoggerFactory.getLogger(V20261025120100__CopyNewcomerKakaoIdToMembers::class.java)

    override fun migrate(context: Context) {
        val connection = context.connection
        val legacy = mutableListOf<LegacyValue>()
        connection
            .prepareStatement(
                """
                SELECT np.id, np.member_id, np.kakao_id
                FROM newcomer_profiles np
                JOIN members m ON m.id = np.member_id
                WHERE np.kakao_id IS NOT NULL
                  AND m.kakao_id IS NULL
                  AND np.deleted_at IS NULL
                  AND m.deleted_at IS NULL
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        legacy += LegacyValue(rs.getLong("id"), rs.getLong("member_id"), rs.getString("kakao_id"))
                    }
                }
            }

        var undecryptable = 0
        val moved =
            connection
                .prepareStatement(
                    """
                    UPDATE members SET kakao_id = ?, pii_key_id = ?, updated_at = now()
                    WHERE id = ? AND kakao_id IS NULL
                    """.trimIndent(),
                ).use { update ->
                    legacy.forEach { row ->
                        val value =
                            runCatching { PiiCryptoContext.decrypt(row.stored, LEGACY_CONTEXT) }
                                .onFailure {
                                    undecryptable++
                                    log.warn("Skipping undecryptable kakao_id on newcomer profile {}", row.profileId)
                                }.getOrNull()
                                ?.trim()
                                ?.ifBlank { null } ?: return@forEach
                        update.setString(1, PiiCryptoContext.encrypt(value, MEMBER_KAKAO_ID_CONTEXT))
                        update.setString(2, PiiCryptoContext.storageKeyId())
                        update.setLong(3, row.memberId)
                        update.addBatch()
                    }
                    update.executeBatch().sum()
                }

        log.info("Copied kakao_id onto {} members; skipped {} undecryptable rows", moved, undecryptable)
    }

    private class LegacyValue(
        val profileId: Long,
        val memberId: Long,
        val stored: String,
    )

    companion object {
        const val LEGACY_CONTEXT = "newcomer_profiles.kakao_id"
    }
}
