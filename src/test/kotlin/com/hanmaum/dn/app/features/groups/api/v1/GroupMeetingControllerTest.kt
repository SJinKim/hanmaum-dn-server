package com.hanmaum.dn.app.features.groups.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.groups.service.GroupMeetingService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.test.Test

/** #286: 순모임 are created by an admin, reports come from an admin or a 순장. */
@WebMvcTest(GroupMeetingController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class GroupMeetingControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var service: GroupMeetingService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val meetingId: UUID = UUID.randomUUID()

    private val createBody =
        """{"groupId":"${UUID.randomUUID()}","meetingTime":"2026-10-11T14:00:00+02:00","location":"본당"}"""

    private fun withRole(role: String?) =
        jwt()
            .jwt { it.subject("kc-002") }
            .authorities(listOfNotNull(role).map { SimpleGrantedAuthority("ROLE_$it") })

    private fun createAs(role: String?) =
        mockMvc.perform(
            post("/api/v1/group-meetings").with(withRole(role)).contentType(MediaType.APPLICATION_JSON).content(createBody),
        )

    private fun reportAs(role: String?) =
        mockMvc.perform(
            post("/api/v1/group-meetings/$meetingId/report")
                .with(withRole(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"entries":[]}"""),
        )

    @Test
    fun `POST meeting is forbidden for a plain member`() {
        createAs(null).andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `POST meeting is forbidden for a group leader`() {
        createAs("GROUP_LEADER").andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `POST meeting is allowed for an admin`() {
        createAs("ADMIN").andExpect(status().isCreated)
    }

    @Test
    fun `POST report is forbidden for a plain member`() {
        reportAs(null).andExpect(status().isForbidden)
        verifyNoInteractions(service)
    }

    @Test
    fun `POST report passes the leader's subject, not a client value`() {
        reportAs("GROUP_LEADER").andExpect(status().isOk)
        verify(service).submitReport(eq(meetingId), any(), eq("kc-002"), eq(false))
    }
}
