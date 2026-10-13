package com.hanmaum.dn.app.features.bulletin.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinEditionResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinEditionSummary
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinSectionTitleResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinServiceResponse
import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinSundayOption
import com.hanmaum.dn.app.features.bulletin.domain.BulletinEdition
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSectionKey
import com.hanmaum.dn.app.features.bulletin.domain.BulletinService
import com.hanmaum.dn.app.features.bulletin.domain.BulletinStatus
import com.hanmaum.dn.app.features.bulletin.service.BulletinDefaults
import com.hanmaum.dn.app.features.bulletin.service.BulletinEditionService
import com.hanmaum.dn.app.features.bulletin.service.BulletinSettingsService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test

/** Bulletin role enforcement; reading the published 주보 needs any login. */
@WebMvcTest(
    controllers = [BulletinAdminController::class, BulletinSettingsController::class, BulletinController::class],
    excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class],
)
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class BulletinControllersTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var editions: BulletinEditionService

    @MockitoBean private lateinit var settings: BulletinSettingsService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val editionId: UUID = UUID.randomUUID()

    private fun withRole(role: String?) =
        jwt()
            .jwt { it.subject("kc-002") }
            .authorities(listOfNotNull(role).map { SimpleGrantedAuthority("ROLE_$it") })

    @Test
    fun `every bulletin endpoint requires authentication`() {
        for (path in listOf("/api/v1/bulletins/current", "/api/v1/admin/bulletins", "/api/v1/admin/bulletin/services")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized)
        }
        verifyNoInteractions(editions, settings)
    }

    @Test
    fun `admin endpoints are forbidden for a plain member and a group leader`() {
        for (role in listOf(null, "GROUP_LEADER")) {
            mockMvc.perform(get("/api/v1/admin/bulletins").with(withRole(role))).andExpect(status().isForbidden)
            mockMvc.perform(post("/api/v1/admin/bulletins/$editionId/publish").with(withRole(role))).andExpect(status().isForbidden)
            mockMvc.perform(get("/api/v1/admin/bulletin/section-titles").with(withRole(role))).andExpect(status().isForbidden)
        }
        verifyNoInteractions(editions, settings)
    }

    @Test
    fun `an admin publishes with the token's subject`() {
        mockMvc.perform(post("/api/v1/admin/bulletins/$editionId/publish").with(withRole("ADMIN"))).andExpect(status().isOk)
        verify(editions).publish(editionId, "kc-002")
    }

    @Test
    fun `a note_taker edits and publishes like an admin`() {
        mockMvc.perform(post("/api/v1/admin/bulletins/$editionId/publish").with(withRole("NOTE_TAKER"))).andExpect(status().isOk)
        verify(editions).publish(editionId, "kc-002")

        mockMvc.perform(delete("/api/v1/admin/bulletins/$editionId").with(withRole("NOTE_TAKER"))).andExpect(status().isNoContent)
        verify(editions).delete(editionId)
    }

    @Test
    fun `a note_taker reaches the bulletin settings`() {
        val serviceId = UUID.randomUUID()
        `when`(settings.deleteService(serviceId, "kc-002")).thenReturn(null)
        mockMvc
            .perform(delete("/api/v1/admin/bulletin/services/$serviceId").with(withRole("NOTE_TAKER")))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deleting a draft is 204`() {
        mockMvc.perform(delete("/api/v1/admin/bulletins/$editionId").with(withRole("ADMIN"))).andExpect(status().isNoContent)
        verify(editions).delete(editionId)
    }

    @Test
    fun `deleting a used service is 200, an unused one 204`() {
        val serviceId = UUID.randomUUID()
        `when`(settings.deleteService(serviceId, "kc-002")).thenReturn(null)
        mockMvc
            .perform(delete("/api/v1/admin/bulletin/services/$serviceId").with(withRole("ADMIN")))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `a member sees 404 while nothing is published`() {
        `when`(editions.currentView()).thenReturn(null)
        mockMvc.perform(get("/api/v1/bulletins/current").with(withRole(null))).andExpect(status().isNotFound)
    }

    @Test
    fun `defaults expose existing edition status and the forward cursor`() {
        val date = LocalDate.of(2026, 10, 11)
        val from = date.plusWeeks(12)
        val churchService = BulletinService(name = "3부", startTime = LocalTime.of(14, 0), sortOrder = 3)
        `when`(editions.defaults(from)).thenReturn(
            BulletinDefaults(date, churchService, listOf(BulletinSundayOption(from, editionId, BulletinStatus.DRAFT)), from.plusWeeks(12)),
        )
        mockMvc
            .perform(get("/api/v1/admin/bulletins/defaults").param("from", from.toString()).with(withRole("ADMIN")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.serviceDate").value(date.toString()))
            .andExpect(jsonPath("$.data.sundays[0].editionPublicId").value(editionId.toString()))
            .andExpect(jsonPath("$.data.sundays[0].status").value("DRAFT"))
            .andExpect(jsonPath("$.data.nextFrom").value(from.plusWeeks(12).toString()))
        verify(editions).defaults(from)
    }

    @Test
    fun `a member cannot read creation defaults`() {
        mockMvc.perform(get("/api/v1/admin/bulletins/defaults").with(withRole(null))).andExpect(status().isForbidden)
        verifyNoInteractions(editions)
    }

    @Test
    fun `a malformed cursor is a 400 before reaching the service`() {
        mockMvc
            .perform(get("/api/v1/admin/bulletins/defaults").param("from", "not-a-date").with(withRole("ADMIN")))
            .andExpect(status().isBadRequest)
        verifyNoInteractions(editions)
    }

    @Test
    fun `a member receives 404 when the service hides a future Sunday`() {
        val future = LocalDate.of(2026, 10, 18)
        `when`(editions.publishedView(future)).thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Bulletin edition not found"))
        mockMvc
            .perform(get("/api/v1/bulletins").param("date", future.toString()).with(withRole(null)))
            .andExpect(status().isNotFound)
        verify(editions).publishedView(future)
    }

    @Test
    fun `history forwards pagination and serializes the filtered page metadata`() {
        val row = BulletinEditionSummary(editionId, LocalDate.of(2026, 10, 4), 41, BulletinStatus.PUBLISHED, "Previous", "3부 예배", null)
        `when`(editions.history(2, 1)).thenReturn(PageImpl(listOf(row), PageRequest.of(2, 1), 3))
        mockMvc
            .perform(get("/api/v1/bulletins/history").param("page", "2").param("size", "1").with(withRole(null)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content[0].publicId").value(editionId.toString()))
            .andExpect(jsonPath("$.data.content[0].serviceDate").value("2026-10-04"))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(3))
            .andExpect(jsonPath("$.data.last").value(true))
        verify(editions).history(2, 1)
    }

    private fun saveSongs(vararg songs: String) =
        mockMvc.perform(
            put("/api/v1/admin/bulletins/$editionId")
                .with(withRole("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"version":0,"songs":[${songs.joinToString(",") { "\"$it\"" }}]}"""),
        )

    @Test
    fun `a blank or too long song is rejected before the service`() {
        saveSongs("   ").andExpect(status().isBadRequest)
        saveSongs("가".repeat(201)).andExpect(status().isBadRequest)
        verifyNoInteractions(editions)
    }

    @Test
    fun `a song of 200 characters passes`() {
        saveSongs("가".repeat(200)).andExpect(status().isOk)
    }

    @ParameterizedTest
    @ValueSource(strings = ["GENERAL_OPERATIONS_VIEWER", "GENERAL_OPERATIONS_EDITOR", "ADMIN", "PASTOR", "NOTE_TAKER"])
    fun `bulletin readers can list defaults detail and settings`(role: String) {
        val date = LocalDate.of(2026, 10, 18)
        val churchService = BulletinService(name = "3부", startTime = LocalTime.of(14, 0), sortOrder = 3)
        val edition = BulletinEdition(date, churchService, "kc-002")
        val response = BulletinEditionResponse.from(edition, emptyList())
        val key = BulletinSectionKey.entries.first()
        `when`(editions.list(null, 0, 20)).thenReturn(PageImpl(listOf(BulletinEditionSummary.from(edition))))
        `when`(editions.defaults(null)).thenReturn(BulletinDefaults(date, churchService, emptyList(), date.plusWeeks(12)))
        `when`(editions.view(editionId)).thenReturn(response)
        `when`(settings.listServices()).thenReturn(listOf(BulletinServiceResponse.from(churchService)))
        `when`(settings.listSectionTitles()).thenReturn(listOf(BulletinSectionTitleResponse(key, "제목", "기본")))
        mockMvc
            .perform(get("/api/v1/admin/bulletins").with(withRole(role)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content[0].serviceDate").value(date.toString()))
        mockMvc
            .perform(get("/api/v1/admin/bulletins/defaults").with(withRole(role)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.serviceDate").value(date.toString()))
        mockMvc
            .perform(get("/api/v1/admin/bulletins/$editionId").with(withRole(role)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.publicId").value(edition.publicId.toString()))
        mockMvc
            .perform(get("/api/v1/admin/bulletin/services").with(withRole(role)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].name").value("3부"))
        mockMvc
            .perform(get("/api/v1/admin/bulletin/section-titles").with(withRole(role)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].title").value("제목"))
    }

    private fun bulletinWriteRequests() =
        listOf(
            post("/api/v1/admin/bulletins").content("{}"),
            put("/api/v1/admin/bulletins/$editionId").content("""{"version":0}"""),
            post("/api/v1/admin/bulletins/$editionId/publish"),
            post("/api/v1/admin/bulletins/$editionId/withdraw"),
            delete("/api/v1/admin/bulletins/$editionId"),
            post("/api/v1/admin/bulletin/services").content("""{"name":"3부","startTime":"14:00"}"""),
            put("/api/v1/admin/bulletin/services/$editionId").content("""{"name":"3부","startTime":"14:00"}"""),
            delete("/api/v1/admin/bulletin/services/$editionId"),
            put("/api/v1/admin/bulletin/section-titles/${BulletinSectionKey.entries.first()}").content("""{"title":"제목"}"""),
        )

    @ParameterizedTest
    @ValueSource(strings = ["GENERAL_OPERATIONS_VIEWER", "MEMBER", "GROUP_LEADER", "MINISTRY_LEADER"])
    fun `roles without bulletin write access cannot change editions or settings`(role: String) {
        for (request in bulletinWriteRequests()) {
            mockMvc.perform(request.with(withRole(role)).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden)
        }
        verifyNoInteractions(editions, settings)
    }

    @ParameterizedTest
    @ValueSource(strings = ["GENERAL_OPERATIONS_EDITOR", "ADMIN", "PASTOR", "NOTE_TAKER"])
    fun `bulletin editors can create update publish withdraw delete and configure`(role: String) {
        val churchService = BulletinService(name = "3부", startTime = LocalTime.of(14, 0), sortOrder = 3)
        val edition = BulletinEdition(LocalDate.of(2026, 10, 18), churchService, "kc-002")
        val response = BulletinEditionResponse.from(edition, emptyList())
        val key = BulletinSectionKey.entries.first()
        `when`(editions.createDraft(null, null, null, "kc-002")).thenReturn(edition)
        `when`(editions.view(edition.publicId)).thenReturn(response)
        `when`(editions.view(editionId)).thenReturn(response)
        `when`(editions.update(eq(editionId), any(), eq("kc-002"))).thenReturn(response)
        `when`(settings.createService(any(), eq("kc-002"))).thenReturn(BulletinServiceResponse.from(churchService))
        `when`(settings.updateService(eq(editionId), any(), eq("kc-002"))).thenReturn(BulletinServiceResponse.from(churchService))
        `when`(settings.deleteService(editionId, "kc-002")).thenReturn(null)
        `when`(settings.updateSectionTitle(key, "제목", "kc-002")).thenReturn(BulletinSectionTitleResponse(key, "제목", "기본"))
        val expectedStatuses = listOf(201, 200, 200, 200, 204, 201, 200, 204, 200)
        bulletinWriteRequests().zip(expectedStatuses).forEach { (request, expected) ->
            val result =
                mockMvc
                    .perform(request.with(withRole(role)).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().`is`(expected))
            if (expected != 204) result.andExpect(jsonPath("$.success").value(true))
        }
        verify(editions).createDraft(null, null, null, "kc-002")
        verify(editions).update(eq(editionId), any(), eq("kc-002"))
        verify(editions).publish(editionId, "kc-002")
        verify(editions).withdraw(editionId, "kc-002")
        verify(editions).delete(editionId)
        verify(settings).createService(any(), eq("kc-002"))
        verify(settings).updateService(eq(editionId), any(), eq("kc-002"))
        verify(settings).deleteService(editionId, "kc-002")
        verify(settings).updateSectionTitle(key, "제목", "kc-002")
    }
}
