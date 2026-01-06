package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.api.toEntity
import com.hanmaum.dn.app.features.members.api.updateForm
import com.hanmaum.dn.app.features.members.data.MemberRepository
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.EntityNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/members")
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
            .orElseThrow { EntityNotFoundException() }
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
            .orElseThrow { EntityNotFoundException() }
        existingMember.updateForm(request)
        return memberRepository.save(existingMember)
    }
}