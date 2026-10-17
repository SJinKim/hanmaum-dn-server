package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberGroupRequiredException
import com.hanmaum.dn.app.features.members.service.MemberPurgeService
import com.hanmaum.dn.app.features.members.service.MemberService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/** HDN-314: the 400 a client branches on to mark the 순 field. */
@WebMvcTest(MemberController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MemberGroupRequiredControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var memberService: MemberService

    @MockitoBean private lateinit var memberPurgeService: MemberPurgeService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val admin = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    @Test
    fun `patch that leaves an active member without a group returns 400 MEMBER_GROUP_REQUIRED`() {
        val publicId = UUID.randomUUID()
        `when`(memberService.updateMember(eq(publicId), any<UpdateMemberRequest>()))
            .thenThrow(MemberGroupRequiredException())

        mockMvc
            .perform(
                patch("/api/v1/members/{publicId}", publicId)
                    .with(admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"memberStatus":"ACTIVE","groupPublicId":null}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("MEMBER_GROUP_REQUIRED"))
            .andExpect(jsonPath("$.message").value("순 배정은 필수입니다. 순을 배정해 주세요."))
            .andExpect(jsonPath("$.fieldErrors.groupPublicId").value("순 배정은 필수입니다. 순을 배정해 주세요."))
    }
}
