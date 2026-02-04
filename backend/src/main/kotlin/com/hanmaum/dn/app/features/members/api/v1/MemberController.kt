package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.common.dto.ApiResponse
import com.hanmaum.dn.app.features.members.api.toDto
import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberDto
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberResponse
import com.hanmaum.dn.app.features.members.api.v1.dto.RegisterMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.service.MemberService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.slf4j.LoggerFactory

@RestController
@RequestMapping("/members")
class MemberController(
    private val memberService: MemberService // <-- Service statt Repository!
) {
    private val logger = LoggerFactory.getLogger(MemberController::class.java)

    @GetMapping
    fun getAllMembers(): List<Member> {
        return memberService.getAllMembers()
    }

    @GetMapping("/{id}")
    fun getMember(@PathVariable id: String): MemberDto {
        return memberService.getMember(id).toDto()
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createMember(@RequestBody request: CreateMemberRequest): Member {
        return memberService.createMember(request)
    }

    @PutMapping("/{id}")
    fun updateMember(@PathVariable id: String, @RequestBody request: UpdateMemberRequest): MemberDto {
        return memberService.updateMember(id, request).toDto()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMember(@PathVariable id: String) {
        memberService.softDeleteMember(id)
    }

    @PostMapping("/register")
    fun registerMember(@RequestBody request: RegisterMemberRequest): ResponseEntity<ApiResponse<Unit>> {
        return try {
            memberService.registerMember(request)

            // 201 Created + Standard Body
            ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(message = "등록이 완료되었습니다."))

        } catch (e: IllegalArgumentException) {
            // 409 Conflict (z.B. Email existiert schon)
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(e.message ?: "이미 등록된 이메일 주소입니다."))

        } catch (e: Exception) {
            e.printStackTrace()
            // 400 Bad Request (Sonstiger Fehler)
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("등록이 실패하였습니다."))
        }
    }

    @GetMapping("/me")
    fun getMyProfile(@AuthenticationPrincipal token: Jwt): ResponseEntity<MemberResponse> {
        logger.info("DEBUG: /me wurde aufgerufen")

        val claims = token.claims
        logger.info("DEBUG: Token Claims: $claims")

        val email = token.getClaimAsString("email") ?: ""
        logger.info("DEBUG: Extrahierte Email: $email")

        if (email.isNullOrBlank()) {
            logger.error("Fehler: Keine Email im Token gefunden!")
            return ResponseEntity.status(
                HttpStatus.UNAUTHORIZED).build()
        }
        try {
            val response = memberService.getMemberProfile(email)
            logger.info("DEBUG: User gefunden, Status: ${response.status}")
            return ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("FEHLER beim Laden des Profils: ${e.message}")
            // Wichtig: Wirf die Exception weiter oder gib 404 zurück, damit wir es sehen
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User DB Error: ${e.message}")
        }
    }
}