package com.hanmaum.dn.app.features.groups.service

import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.domain.Member
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Puts a member without a 순 into the 새가족 순 (HDN-313).
 *
 * A 새가족 created from the form or the import has no 순 and no account. When that person
 * later links an app account, they would otherwise be ACTIVE without a 순, able to check in
 * and counted nowhere. An existing 순 is never replaced. Without a marked 새가족 순 the link
 * still goes through and only a warning is logged.
 */
@Component
class NewcomerGroupAssigner(
    private val churchGroupRepository: ChurchGroupRepository,
) {
    private val log = LoggerFactory.getLogger(NewcomerGroupAssigner::class.java)

    fun assignIfUngrouped(member: Member) {
        if (member.group != null) return
        val newcomerGroup = churchGroupRepository.findFirstByNewcomerGroupTrueAndDeletedAtIsNull()
        if (newcomerGroup == null) {
            log
                .atWarn()
                .addKeyValue("event.action", "member.newcomer_group.assign")
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("member.id", member.id)
                .log("No 새가족 group is marked; member stays without a group")
            return
        }
        member.group = newcomerGroup
        log
            .atInfo()
            .addKeyValue("event.action", "member.newcomer_group.assign")
            .addKeyValue("event.outcome", "success")
            .addKeyValue("member.id", member.id)
            .addKeyValue("group.id", newcomerGroup.id)
            .log("Assigned ungrouped member to the 새가족 group")
    }
}
