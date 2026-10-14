package com.hanmaum.dn.app.common.config

import com.hanmaum.dn.app.features.groups.api.v1.ChurchGroupController
import com.hanmaum.dn.app.features.groups.api.v1.TrainingMatrixController
import com.hanmaum.dn.app.features.groups.service.ChurchGroupService
import com.hanmaum.dn.app.features.groups.service.TrainingMatrixService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
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

@WebMvcTest(
    controllers = [ChurchGroupDefaultProbe::class, TrainingMatrixDefaultProbe::class],
    excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class],
)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class TrainingControllerDefaultsTest {
    @Autowired private lateinit var mvc: MockMvc

    @MockitoBean private lateinit var groups: ChurchGroupService

    @MockitoBean private lateinit var matrix: TrainingMatrixService

    @MockitoBean private lateinit var members: MemberRepository

    @MockitoBean private lateinit var decoder: JwtDecoder

    private val paths = listOf("/security-probes/groups", "/security-probes/matrix")

    @ParameterizedTest
    @ValueSource(strings = ["TRAINING_VIEWER", "TRAINING_EDITOR", "MEMBER"])
    fun `unannotated training handlers remain admin only`(role: String) {
        for (path in paths) {
            mvc
                .perform(post("$path/unannotated").with(jwt().authorities(SimpleGrantedAuthority("ROLE_$role"))))
                .andExpect(status().isForbidden)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["ADMIN", "PASTOR"])
    fun `unannotated training handlers preserve administrative access`(role: String) {
        for (path in paths) {
            mvc
                .perform(post("$path/unannotated").with(jwt().authorities(SimpleGrantedAuthority("ROLE_$role"))))
                .andExpect(status().isOk)
                .andExpect(content().string("ok"))
        }
    }
}

@RestController
@RequestMapping("/security-probes/groups")
private class ChurchGroupDefaultProbe(
    service: ChurchGroupService,
) : ChurchGroupController(service) {
    @PostMapping("/unannotated")
    fun unannotated(): String = "ok"
}

@RestController
@RequestMapping("/security-probes/matrix")
private class TrainingMatrixDefaultProbe(
    service: TrainingMatrixService,
) : TrainingMatrixController(service) {
    @PostMapping("/unannotated")
    fun unannotated(): String = "ok"
}
