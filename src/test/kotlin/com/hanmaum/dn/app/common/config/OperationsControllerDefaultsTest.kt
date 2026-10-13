package com.hanmaum.dn.app.common.config

import com.hanmaum.dn.app.features.attendance.api.v1.AttendanceLogController
import com.hanmaum.dn.app.features.attendance.service.AttendanceRosterService
import com.hanmaum.dn.app.features.bulletin.api.v1.BulletinAdminController
import com.hanmaum.dn.app.features.bulletin.api.v1.BulletinSettingsController
import com.hanmaum.dn.app.features.bulletin.service.BulletinEditionService
import com.hanmaum.dn.app.features.bulletin.service.BulletinSettingsService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Exercise the real controllers' inherited default on an otherwise unannotated future handler. */
@WebMvcTest(
    controllers = [AttendanceLogDefaultProbe::class, BulletinDefaultProbe::class, BulletinSettingsDefaultProbe::class],
    excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class],
)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class OperationsControllerDefaultsTest {
    @Autowired private lateinit var mvc: MockMvc

    @MockitoBean private lateinit var roster: AttendanceRosterService

    @MockitoBean private lateinit var editions: BulletinEditionService

    @MockitoBean private lateinit var settings: BulletinSettingsService

    @MockitoBean private lateinit var members: MemberRepository

    @MockitoBean private lateinit var decoder: JwtDecoder

    private val paths = listOf("/security-probes/logs", "/security-probes/bulletins", "/security-probes/settings")

    @ParameterizedTest
    @ValueSource(strings = ["MEMBER", "GENERAL_OPERATIONS_VIEWER", "GROUP_LEADER", "MINISTRY_LEADER"])
    fun `an unannotated handler stays closed to roles without write access`(role: String) {
        for (path in paths) {
            mvc
                .perform(post("$path/unannotated-write").with(jwt().authorities(SimpleGrantedAuthority("ROLE_$role"))))
                .andExpect(status().isForbidden)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["ADMIN", "PASTOR", "NOTE_TAKER", "GENERAL_OPERATIONS_EDITOR"])
    fun `an unannotated handler inherits the controller write access`(role: String) {
        for (path in paths) {
            mvc
                .perform(post("$path/unannotated-write").with(jwt().authorities(SimpleGrantedAuthority("ROLE_$role"))))
                .andExpect(status().isOk)
                .andExpect(content().string("ok"))
        }
    }

    @Test
    fun `an unannotated handler requires authentication`() {
        for (path in paths) mvc.perform(post("$path/unannotated-write")).andExpect(status().isUnauthorized)
    }
}

@RestController
@RequestMapping("/security-probes/logs")
private class AttendanceLogDefaultProbe(
    service: AttendanceRosterService,
) : AttendanceLogController(service) {
    @PostMapping("/unannotated-write")
    fun unannotatedWrite(): String = "ok"
}

@RestController
@RequestMapping("/security-probes/bulletins")
private class BulletinDefaultProbe(
    service: BulletinEditionService,
) : BulletinAdminController(service) {
    @PostMapping("/unannotated-write")
    fun unannotatedWrite(): String = "ok"
}

@RestController
@RequestMapping("/security-probes/settings")
private class BulletinSettingsDefaultProbe(
    service: BulletinSettingsService,
) : BulletinSettingsController(service) {
    @PostMapping("/unannotated-write")
    fun unannotatedWrite(): String = "ok"
}
