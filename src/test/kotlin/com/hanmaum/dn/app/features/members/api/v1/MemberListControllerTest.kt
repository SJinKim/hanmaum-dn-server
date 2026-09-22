package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberSummaryDto
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberService
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.Test

@WebMvcTest(MemberController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MemberListControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var memberService: MemberService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `GET members forwards all server-side list filters and sort orders`() {
        val groupId = UUID.randomUUID()
        val ministryId = UUID.randomUUID()
        val sort = listOf("groupName,asc", "lastName,desc")
        val response =
            PageImpl(
                listOf(
                    MemberSummaryDto(
                        publicId = UUID.randomUUID().toString(),
                        lastName = "김",
                        firstName = "철수",
                        memberStatus = "ACTIVE",
                    ),
                ),
            )
        `when`(
            memberService.getMembers(
                search = "김",
                status = null,
                baptism = null,
                groupPublicId = groupId,
                unassigned = false,
                trainingCode = "QT_BASIC_SEMINAR",
                ministryPublicId = ministryId,
                sort = sort,
                page = 1,
                size = 50,
            ),
        ).thenReturn(response)

        mockMvc
            .perform(
                get("/api/v1/members")
                    .param("search", "김")
                    .param("groupPublicId", groupId.toString())
                    .param("unassigned", "false")
                    .param("trainingCode", "QT_BASIC_SEMINAR")
                    .param("ministryPublicId", ministryId.toString())
                    .param("sort", "groupName,asc")
                    .param("sort", "lastName,desc")
                    .param("page", "1")
                    .param("size", "50")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content[0].lastName").value("김"))

        verify(memberService).getMembers(
            search = "김",
            status = null,
            baptism = null,
            groupPublicId = groupId,
            unassigned = false,
            trainingCode = "QT_BASIC_SEMINAR",
            ministryPublicId = ministryId,
            sort = sort,
            page = 1,
            size = 50,
        )
    }
}
