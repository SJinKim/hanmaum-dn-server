package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.ws.rs.ProcessingException
import org.keycloak.admin.client.Keycloak
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Service
class MemberPurgeService(
    private val memberRepository: MemberRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
    private val keycloak: Keycloak,
    @Value("\${app.keycloak.realm:hanmaum}") private val realm: String,
) {
    private val log = LoggerFactory.getLogger(MemberPurgeService::class.java)

    fun purgeExpired(now: Instant): Int {
        val expiredMembers = memberRepository.findAllByDeleteEntryAtLessThanEqualAndDeletedAtIsNotNull(now)
        expiredMembers.forEach { member ->
            deleteKeycloakUser(member.keycloakId, member.id!!)
            transactionTemplate.executeWithoutResult {
                purgeMemberRows(member.id!!)
            }
        }
        return expiredMembers.size
    }

    private fun deleteKeycloakUser(
        keycloakId: String?,
        memberId: Long,
    ) {
        if (keycloakId.isNullOrBlank()) {
            return
        }

        try {
            keycloak
                .realm(realm)
                .users()
                .delete(keycloakId)
                .use { response ->
                    check(response.status == 204 || response.status == 404) {
                        "Keycloak member purge failed with HTTP ${response.status}."
                    }
                }
        } catch (exception: ProcessingException) {
            throw IllegalStateException("Keycloak member purge transport failure for memberId=$memberId.", exception)
        }
    }

    private fun purgeMemberRows(memberId: Long) {
        jdbcTemplate.update(
            "DELETE FROM car_passengers WHERE car_id IN (SELECT id FROM cars WHERE driver_member_id = ?)",
            memberId,
        )
        jdbcTemplate.update("DELETE FROM car_passengers WHERE member_id = ?", memberId)
        jdbcTemplate.update("DELETE FROM cars WHERE driver_member_id = ?", memberId)
        jdbcTemplate.update("DELETE FROM meeting_attendances WHERE member_id = ?", memberId)
        jdbcTemplate.update("DELETE FROM attendance_logs WHERE member_id = ?", memberId)
        jdbcTemplate.update("DELETE FROM ministry_registrations WHERE member_id = ?", memberId)
        jdbcTemplate.update("DELETE FROM user_training WHERE user_id = ?", memberId)
        jdbcTemplate.update(
            "DELETE FROM family_relationships WHERE member_id = ? OR related_member_id = ?",
            memberId,
            memberId,
        )
        memberRepository.deleteById(memberId)
        memberRepository.flush()
        log.info("Permanently purged member memberId={}", memberId)
    }
}
