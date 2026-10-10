package com.hanmaum.dn.app.features.ministry.service

import com.hanmaum.dn.app.features.members.service.CurrentMemberResolver
import com.hanmaum.dn.app.features.ministry.domain.Ministry
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignmentRole
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/**
 * Who owns a ministry.
 *
 * `MINISTRY_LEADER` is one realm role shared by every 사역 leader, so it only says "leads some
 * ministry". Which ministry is answered here, from the current `LEADER` assignment in the DB.
 * Every write path that admits `MINISTRY_LEADER` for a single ministry must call
 * [requireLeader]; otherwise the leader of one ministry can change any other (server#300).
 * `SUB_LEADER` deliberately does not count as owner.
 */
@Component
class MinistryOwnership(
    private val assignmentRepository: MinistryAssignmentRepository,
    private val currentMemberResolver: CurrentMemberResolver,
) {
    /** The current `LEADER` assignment of [ministry], or null when nobody leads it. */
    fun currentLeader(ministry: Ministry): MinistryAssignment? =
        assignmentRepository
            .findCurrentByMinistryIds(listOf(ministry.id!!))
            .firstOrNull { it.role == MinistryAssignmentRole.LEADER }

    /**
     * Passes for an admin or for the current leader of [ministry]; anyone else gets 403 with
     * [deniedMessage].
     */
    fun requireLeader(
        ministry: Ministry,
        keycloakSubject: String,
        isAdmin: Boolean,
        deniedMessage: String = "Only this ministry's leader can change it",
    ) {
        if (isAdmin) return
        val caller = currentMemberResolver.require(keycloakSubject)
        if (currentLeader(ministry)?.member?.id != caller.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, deniedMessage)
        }
    }
}
