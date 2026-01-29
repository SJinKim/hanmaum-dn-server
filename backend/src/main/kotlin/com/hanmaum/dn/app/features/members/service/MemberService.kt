package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.api.toEntity
import com.hanmaum.dn.app.features.members.api.updateForm
import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.transaction.annotation.Transactional
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class MemberService (
    private val membersRepository: MemberRepository,
    private val churchGroupRepository: ChurchGroupRepository
) {
    // Helper: String -> UUID
    private fun parseId(id: String): UUID {
        return try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            throw EntityNotFoundException("Invalid ID format")
        }
    }

    @Transactional(readOnly=true)
    fun getAllMembers(): List<Member> = membersRepository.findAll()

    @Transactional
    fun getMember(publicIdStr: String): Member {
        val uuid = parseId(publicIdStr)
        return membersRepository.findByPublicId(uuid)
            .orElseThrow { EntityNotFoundException("Member not found") }
    }

    @Transactional
    fun createMember(request: CreateMemberRequest): Member {
        val newMember = request.toEntity()

        if(request.groupId != null) {
            val group = churchGroupRepository.findById(request.groupId)
                .orElseThrow{ EntityNotFoundException("Group with ID ${request.groupId} not found") }
            newMember.group = group
        }
        return membersRepository.save(newMember)
    }

    @Transactional
    fun updateMember(publicIdStr: String, request: UpdateMemberRequest): Member {
        val member = getMember(publicIdStr)
        member.updateForm(request)

        if(request.groupId != null) {
            if (member.group?.id != request.groupId) {
                val group = churchGroupRepository.findById(request.groupId)
                    .orElseThrow { EntityNotFoundException("Group with ID ${request.groupId} not found") }
                member.group = group
            }
        } else {
            // Optional: Wenn null gesendet wird, Gruppe entfernen?
            // member.group = null
            // Entscheide hier: Ignorieren oder Löschen?
            // Aktuell ignorieren wir null im Request (behalten die alte Gruppe).
        }
        return membersRepository.save(member)
    }

    @Transactional
    fun softDeleteMember(publicIdStr: String) {
        val member = getMember(publicIdStr)
        member.deletedAt = LocalDateTime.now()
        member.memberStatus = MemberStatus.DELETED
        membersRepository.save(member)
    }

}