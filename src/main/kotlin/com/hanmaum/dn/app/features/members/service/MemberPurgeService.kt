package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import jakarta.ws.rs.ProcessingException
import org.keycloak.admin.client.Keycloak
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

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

    /**
     * Admin hard delete of one soft-deleted member, ahead of the retention period. Same steps
     * as [purgeExpired]: the Keycloak account goes first (404 counts as gone), then the rows.
     * Afterwards the email can be registered again.
     */
    fun purgeMember(publicId: UUID) {
        val member =
            memberRepository
                .findByPublicId(publicId)
                .orElseThrow { EntityNotFoundException("Member not found: $publicId") }
        if (member.deletedAt == null || member.memberStatus != MemberStatus.DELETED) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제된 회원만 영구 삭제할 수 있습니다.")
        }
        val memberId = member.id!!
        deleteKeycloakUser(member.keycloakId, memberId)
        transactionTemplate.executeWithoutResult {
            purgeMemberRows(memberId)
        }
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
        jdbcTemplate.update("UPDATE newcomer_profiles SET caregiver_member_id = NULL WHERE caregiver_member_id = ?", memberId)
        jdbcTemplate.update("DELETE FROM newcomer_profiles WHERE member_id = ?", memberId)
        jdbcTemplate.update(
            "DELETE FROM newcomer_form_submissions WHERE newcomer_profile_id IN " +
                "(SELECT id FROM newcomer_profiles WHERE member_id = ?)",
            memberId,
        )
        jdbcTemplate.update("DELETE FROM newcomer_graduations WHERE member_id = ?", memberId)
        jdbcTemplate.update("DELETE FROM member_reconciliation_candidates WHERE member_id = ?", memberId)
        jdbcTemplate.update(
            "DELETE FROM member_reconciliation_candidates WHERE reconciliation_id IN " +
                "(SELECT id FROM member_reconciliations WHERE registration_member_id = ? OR selected_member_id = ?)",
            memberId,
            memberId,
        )
        jdbcTemplate.update(
            "DELETE FROM member_reconciliations WHERE registration_member_id = ? OR selected_member_id = ?",
            memberId,
            memberId,
        )
        jdbcTemplate.update("UPDATE newcomer_profiles SET caregiver_member_id = NULL WHERE caregiver_member_id = ?", memberId)
        jdbcTemplate.update("DELETE FROM newcomer_profiles WHERE member_id = ?", memberId)
        jdbcTemplate.update(
            "DELETE FROM car_passengers WHERE car_id IN (SELECT id FROM cars WHERE driver_member_id = ?)",
            memberId,
        )
        jdbcTemplate.update("DELETE FROM car_passengers WHERE member_id = ?", memberId)
        jdbcTemplate.update("DELETE FROM cars WHERE driver_member_id = ?", memberId)
        jdbcTemplate.update("DELETE FROM meeting_attendances WHERE member_id = ?", memberId)
        jdbcTemplate.update("DELETE FROM ministry_registrations WHERE member_id = ?", memberId)
        jdbcTemplate.update("DELETE FROM group_leaders WHERE member_id = ?", memberId)
        jdbcTemplate.update("DELETE FROM user_training WHERE user_id = ?", memberId)
        jdbcTemplate.update("DELETE FROM member_graduations WHERE member_id = ?", memberId)
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
