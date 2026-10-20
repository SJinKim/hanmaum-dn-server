package com.hanmaum.dn.app.features.guide.api.v1

import com.hanmaum.dn.app.common.config.ApiPrefixConfig
import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaResponse
import com.hanmaum.dn.app.features.guide.service.GuideAreaService
import com.hanmaum.dn.app.features.guide.service.GuideException
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(GuideAreaController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class, ApiPrefixConfig::class, GuideExceptionHandler::class, GuideDetailValidationHandler::class)
class GuideAreaControllerTest {
    @Autowired private lateinit var mvc: MockMvc

    @MockitoBean private lateinit var service: GuideAreaService

    @MockitoBean private lateinit var members: MemberRepository

    @MockitoBean private lateinit var decoder: JwtDecoder
    private val id = UUID.randomUUID()
    private val room = UUID.randomUUID()
    private val url = "/api/v1/guide/counting-areas"
    private val create = """{"roomPublicId":"$room","label":"new area","displayOrder":4,"active":true}"""
    private val update = create.dropLast(1) + """, "version":0}"""
    private val response = GuideAreaResponse(id, room, "existing room", "new area", 4, true, 0)

    private fun actor(role: String) = jwt().authorities(SimpleGrantedAuthority("ROLE_$role"))

    @Test fun `configuration and room lookup follow Guide read roles and reject ordinary members and anonymous`() {
        whenever(service.list()).thenReturn(listOf(response))
        whenever(service.rooms()).thenReturn(emptyList())
        for (role in listOf("GUIDE_VIEWER", "GUIDE_EDITOR", "ADMIN", "PASTOR")) {
            mvc
                .perform(get(url).with(actor(role)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data[0].areaPublicId").value(id.toString()))
            mvc.perform(get("$url/rooms").with(actor(role))).andExpect(status().isOk)
        }
        for (path in listOf(url, "$url/rooms")) {
            mvc.perform(get(path).with(actor("MEMBER"))).andExpect(status().isForbidden)
            mvc.perform(get(path)).andExpect(status().isUnauthorized)
        }
    }

    @Test fun `only Guide editors admin and pastor can configure areas`() {
        whenever(service.create(any())).thenReturn(response)
        whenever(service.update(any(), any())).thenReturn(response)
        for (role in listOf("GUIDE_EDITOR", "ADMIN", "PASTOR")) {
            mvc.perform(post(url).with(actor(role)).contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isCreated)
            mvc.perform(put("$url/$id").with(actor(role)).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk)
        }
        for (role in listOf("GUIDE_VIEWER", "MEMBER")) {
            mvc.perform(post(url).with(actor(role)).contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isForbidden)
            mvc
                .perform(
                    put("$url/$id").with(actor(role)).contentType(MediaType.APPLICATION_JSON).content(update),
                ).andExpect(status().isForbidden)
        }
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(create)).andExpect(status().isUnauthorized)
    }

    @Test fun `invalid fields missing version and unknown properties are rejected with field errors`() {
        val cases =
            listOf(
                update.replace("\"version\":0", "\"version\":\"0\""),
                update.replace(", \"version\":0", ""),
                update.replace("\"displayOrder\":4", "\"displayOrder\":1.5"),
                update.replace("\"displayOrder\":4", "\"displayOrder\":-1"),
                update.replace("new area", " "),
                update.replace(room.toString(), "not-a-uuid"),
                update.dropLast(1) + """, "roomCode":"invented"}""",
            )
        cases.forEach { body ->
            mvc
                .perform(put("$url/$id").with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.data.fieldErrors").isNotEmpty())
                .andExpect(jsonPath("$.message").value("Invalid counting-area settings."))
        }
    }

    @Test fun `stale configuration version and unknown area use conflict and not found responses`() {
        whenever(
            service.update(any(), any()),
        ).thenThrow(GuideException(HttpStatus.CONFLICT, "Counting area changed; reload before saving."))
        mvc
            .perform(put("$url/$id").with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(update))
            .andExpect(status().isConflict)
        whenever(service.update(any(), any())).thenThrow(GuideException(HttpStatus.NOT_FOUND, "Counting area not found."))
        mvc
            .perform(put("$url/$id").with(actor("GUIDE_EDITOR")).contentType(MediaType.APPLICATION_JSON).content(update))
            .andExpect(status().isNotFound)
    }
}
