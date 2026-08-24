package com.hanmaum.dn.app.features.members.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.GraduationReason
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.domain.MemberGraduation
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.members.service.MemberGraduationService
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test

// Body shape only. Authorisation is asserted in MemberGraduationSecurityTest.
// SecurityConfig is imported because the handlers take @AuthenticationPrincipal Jwt, whose
// argument resolver only exists once @EnableWebSecurity is on the context; without it Spring
// tries to data-bind Jwt as a model attribute and the request 500s.
@WebMvcTest(
    MemberGraduationController::class,
    excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class],
)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class MemberGraduationControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var graduationService: MemberGraduationService

    // WebMvcConfig registers MemberStatusInterceptor, which the slice instantiates.
    @MockitoBean
    private lateinit var memberRepository: MemberRepository

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private val publicId: UUID = UUID.randomUUID()

    // The controller reads jwt.subject, so the caller must carry a real JWT principal —
    // the repo's other web slices authenticate the same way.
    private fun admin() = jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    private fun graduation(): MemberGraduation {
        val member = Member(lastName = "김", firstName = "철수")
        member.id = 1L
        return MemberGraduation(
            member = member,
            graduatedOn = LocalDate.of(2026, 5, 1),
            reason = GraduationReason.MARRIAGE,
            graduatedBy = "admin-sub-1",
            previousMemberStatus = MemberStatus.ACTIVE,
        )
    }

    @Test
    fun `graduating returns the recorded event`() {
        // anyOrNull() for `note`: it is nullable, and Mockito's any() rejects null.
        `when`(
            graduationService.graduate(any(), any(), any(), anyOrNull(), any()),
        ).thenReturn(graduation())

        mockMvc
            .perform(
                post("/api/v1/members/$publicId/graduation")
                    .with(admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"graduatedOn":"2026-05-01","reason":"MARRIAGE"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.reason").value("MARRIAGE"))
            .andExpect(jsonPath("$.data.graduatedOn").value("2026-05-01"))
            .andExpect(jsonPath("$.data.open").value(true))
    }

    @Test
    fun `graduating without a reason is rejected`() {
        mockMvc
            .perform(
                post("/api/v1/members/$publicId/graduation")
                    .with(admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"graduatedOn":"2026-05-01"}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `reading returns the graduated flag and the history`() {
        `when`(graduationService.openGraduation(publicId)).thenReturn(graduation())
        `when`(graduationService.history(publicId)).thenReturn(listOf(graduation()))

        mockMvc
            .perform(get("/api/v1/members/$publicId/graduation").with(admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.graduated").value(true))
            .andExpect(jsonPath("$.data.history.length()").value(1))
    }

    @Test
    fun `reading a member who never graduated reports not graduated`() {
        `when`(graduationService.openGraduation(publicId)).thenReturn(null)
        `when`(graduationService.history(publicId)).thenReturn(emptyList())

        mockMvc
            .perform(get("/api/v1/members/$publicId/graduation").with(admin()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.graduated").value(false))
    }
}
