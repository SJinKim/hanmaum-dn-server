package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberCountDto
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberPurgeService
import com.hanmaum.dn.app.features.members.service.MemberService
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

@WebMvcTest(MemberController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MemberCountControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var memberService: MemberService

    @MockitoBean private lateinit var memberPurgeService: MemberPurgeService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `GET members count without status counts every non-deleted member for an admin`() {
        `when`(memberService.countMembers(null)).thenReturn(MemberCountDto(42))

        mockMvc
            .perform(get("/api/v1/members/count").with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.count").value(42))
    }

    @Test
    fun `GET members count narrows to one status for a note_taker`() {
        `when`(memberService.countMembers(MemberStatus.PENDING)).thenReturn(MemberCountDto(3))

        mockMvc
            .perform(
                get("/api/v1/members/count")
                    .param("status", "PENDING")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_NOTE_TAKER"))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.count").value(3))
    }

    @Test
    fun `GET members count is closed to a 순장`() {
        mockMvc
            .perform(get("/api/v1/members/count").with(jwt().authorities(SimpleGrantedAuthority("ROLE_GROUP_LEADER"))))
            .andExpect(status().isForbidden)
        verifyNoInteractions(memberService)
    }
}
