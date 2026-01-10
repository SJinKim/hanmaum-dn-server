package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.service.MemberService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberService: MemberService // <-- Service statt Repository!
) {

    @GetMapping
    fun getAllMembers(): List<Member> {
        return memberService.getAllMembers()
    }

    @GetMapping("/{id}")
    fun getMember(@PathVariable id: Long): Member {
        return memberService.getMember(id)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createMember(@RequestBody request: CreateMemberRequest): Member {
        return memberService.createMember(request)
    }

    @PutMapping("/{id}")
    fun updateMember(@PathVariable id: Long, @RequestBody request: UpdateMemberRequest): Member {
        return memberService.updateMember(id, request)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMember(@PathVariable id: Long) {
        memberService.deleteMember(id)
    }
}