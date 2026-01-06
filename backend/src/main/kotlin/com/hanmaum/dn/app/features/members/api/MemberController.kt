package com.hanmaum.dn.app.features.members.api

import com.hanmaum.dn.app.features.members.api.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.data.MemberRepository
import com.hanmaum.dn.app.features.members.domain.Member
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberRepository: MemberRepository
) {

    @GetMapping
    fun getAllMembers(): List<Member> {
        return memberRepository.findAll()
    }

    @GetMapping("/{id}")
    fun getMember(@PathVariable id: Long): Member {
        return memberRepository.findById(id)
            .orElseThrow { jakarta.persistence.EntityNotFoundException() }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMember(@PathVariable id: Long) {
        memberRepository.deleteById(id)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createMember(@RequestBody request: CreateMemberRequest): Member {
        val newMember = request.toEntity()
        return memberRepository.save(newMember)
    }

    @PutMapping("/{id}")
    fun updateMember(@PathVariable id: Long, @RequestBody request: UpdateMemberRequest): Member {
        val existingMember = memberRepository.findById(id)
            .orElseThrow { jakarta.persistence.EntityNotFoundException() }
        existingMember.updateForm(request)
        return memberRepository.save(existingMember)
    }
}