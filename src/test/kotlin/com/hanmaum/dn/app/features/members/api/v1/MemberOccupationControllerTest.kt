package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.api.toDto
import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(MemberController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MemberOccupationControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var memberService: MemberService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    @Test
    fun `admin can create and read member occupation`() {
        val member = Member(lastName = "김", firstName = "민수", occupation = "개발자", memberStatus = MemberStatus.ACTIVE)
        val dto = member.toDto()
        `when`(memberService.createMember(any<CreateMemberRequest>())).thenReturn(dto)
        `when`(memberService.getMemberByPublicId(member.publicId)).thenReturn(dto)

        mockMvc
            .perform(
                post("/api/v1/members")
                    .with(admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"lastName":"김","firstName":"민수","occupation":"개발자"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.occupation").value("개발자"))

        verify(memberService).createMember(check { assertEquals("개발자", it.occupation) })

        mockMvc
            .perform(get("/api/v1/members/{publicId}", member.publicId).with(admin))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.occupation").value("개발자"))
    }

    @Test
    fun `patch without occupation keeps it omitted from service request`() {
        val publicId = UUID.randomUUID()
        val dto = Member(lastName = "김", firstName = "민수", occupation = "개발자", memberStatus = MemberStatus.ACTIVE).toDto()
        `when`(memberService.updateMember(eq(publicId), any<UpdateMemberRequest>()))
            .thenReturn(dto)

        mockMvc
            .perform(
                patch("/api/v1/members/{publicId}", publicId)
                    .with(admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"city":"서울"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.occupation").value("개발자"))

        verify(memberService).updateMember(eq(publicId), check { assertEquals(null, it.occupation) })
    }
}
