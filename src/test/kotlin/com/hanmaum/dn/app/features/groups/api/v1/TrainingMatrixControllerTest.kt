package com.hanmaum.dn.app.features.groups.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.groups.api.v1.dto.NextGroupLeaderResponse
import com.hanmaum.dn.app.features.groups.api.v1.dto.TrainingMatrixMemberDto
import com.hanmaum.dn.app.features.groups.service.TrainingMatrixService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(TrainingMatrixController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class TrainingMatrixControllerTest {
    @Autowired private lateinit var mvc: MockMvc

    @MockitoBean private lateinit var service: TrainingMatrixService

    @MockitoBean private lateinit var members: MemberRepository

    @MockitoBean private lateinit var decoder: JwtDecoder
    private val id = UUID.randomUUID()
    private val path = "/api/v1/church-groups/members"

    @ParameterizedTest
    @ValueSource(strings = ["TRAINING_VIEWER", "TRAINING_EDITOR", "ADMIN", "PASTOR"])
    fun `matrix readers receive only the scoped paginated projection`(role: String) {
        val row = TrainingMatrixMemberDto(id.toString(), "김", "테스트", "ACTIVE", null, null, null, emptyList(), true, false, false, null)
        `when`(service.getMembers(1, 2)).thenReturn(PageImpl(listOf(row), PageRequest.of(1, 2), 5))
        mvc
            .perform(
                get(path)
                    .param("page", "1")
                    .param("size", "2")
                    .param("status", "PENDING")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_$role"))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.number").value(1))
            .andExpect(jsonPath("$.data.totalElements").value(5))
            .andExpect(jsonPath("$.data.content[0].publicId").value(id.toString()))
            .andExpect(jsonPath("$.data.content[0].isNextGroupLeader").value(true))
            .andExpect(jsonPath("$.data.content[0].email").doesNotExist())
            .andExpect(jsonPath("$.data.content[0].appLinked").doesNotExist())
        verify(service).getMembers(1, 2)
    }

    @ParameterizedTest
    @ValueSource(strings = ["TRAINING_EDITOR", "ADMIN", "PASTOR"])
    fun `matrix editors may set and unset only the next leader flag`(role: String) {
        for (value in listOf(true, false)) {
            `when`(service.setNextGroupLeader(id, value)).thenReturn(NextGroupLeaderResponse(id.toString(), value))
            mvc
                .perform(
                    patch("$path/$id/next-leader")
                        .with(jwt().authorities(SimpleGrantedAuthority("ROLE_$role")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"isNextGroupLeader":$value}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.publicId").value(id.toString()))
                .andExpect(jsonPath("$.data.isNextGroupLeader").value(value))
                .andExpect(jsonPath("$.data.firstName").doesNotExist())
            verify(service).setNextGroupLeader(id, value)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["TRAINING_VIEWER", "NOTE_TAKER", "GROUP_LEADER", "MINISTRY_LEADER", "GENERAL_OPERATIONS_EDITOR", "MEMBER"])
    fun `roles without matrix write access cannot set the next leader flag`(role: String) {
        mvc
            .perform(
                patch("$path/$id/next-leader")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_$role")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"isNextGroupLeader":true}"""),
            ).andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @ParameterizedTest
    @ValueSource(strings = ["NOTE_TAKER", "GROUP_LEADER", "MINISTRY_LEADER", "GENERAL_OPERATIONS_EDITOR", "MEMBER"])
    fun `other roles do not gain matrix read access`(role: String) {
        mvc
            .perform(get(path).with(jwt().authorities(SimpleGrantedAuthority("ROLE_$role"))))
            .andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `the required flag cannot be omitted or null`() {
        for (body in listOf("{}", """{"isNextGroupLeader":null}""")) {
            mvc
                .perform(
                    patch("$path/$id/next-leader")
                        .with(jwt().authorities(SimpleGrantedAuthority("ROLE_TRAINING_EDITOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                ).andExpect(status().isBadRequest)
        }
        verifyNoInteractions(service)
    }

    @Test
    fun `matrix reads and writes require authentication`() {
        mvc.perform(get(path)).andExpect(status().isUnauthorized)
        mvc
            .perform(
                patch("$path/$id/next-leader")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"isNextGroupLeader":true}"""),
            ).andExpect(status().isUnauthorized)
        verifyNoInteractions(service)
    }
}
