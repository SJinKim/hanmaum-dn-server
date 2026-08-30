package com.hanmaum.dn.app.features.announcements.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.common.domainvalue.AnnouncementCategory
import com.hanmaum.dn.app.features.announcements.api.v1.dto.CreateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.api.v1.dto.UpdateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.domain.Announcement
import com.hanmaum.dn.app.features.announcements.service.AnnouncementService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

@WebMvcTest(AnnouncementController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class AnnouncementControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var announcementService: AnnouncementService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val announcement =
        Announcement(
            category = AnnouncementCategory.EVENT,
            title = "여름 수련회",
            body = "수련회 안내",
            startAt = OffsetDateTime.of(2026, 8, 30, 10, 0, 0, 0, ZoneOffset.UTC),
            imageUrl = "https://cdn.example.org/retreat.jpg",
            location = "교회 본당",
            viewCount = 124,
        )
    private val publicId = announcement.publicId

    @Test
    fun `GET announcements returns enriched feed without authentication`() {
        `when`(announcementService.getActiveAnnouncements()).thenReturn(listOf(announcement))

        mockMvc
            .perform(get("/api/v1/announcements"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].imageUrl").value("https://cdn.example.org/retreat.jpg"))
            .andExpect(jsonPath("$.data[0].location").value("교회 본당"))
            .andExpect(jsonPath("$.data[0].viewCount").value(124))
    }

    @Test
    fun `GET announcement detail returns enriched fields for authenticated member`() {
        `when`(announcementService.getActiveAnnouncement(publicId)).thenReturn(announcement)

        mockMvc
            .perform(
                get("/api/v1/announcements/{publicId}", publicId)
                    .with(jwt().jwt { it.subject("kc-001") }),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.id").value(publicId.toString()))
            .andExpect(jsonPath("$.data.imageUrl").value("https://cdn.example.org/retreat.jpg"))
            .andExpect(jsonPath("$.data.location").value("교회 본당"))
            .andExpect(jsonPath("$.data.viewCount").value(124))
    }

    @Test
    fun `GET announcement detail requires authentication`() {
        mockMvc
            .perform(get("/api/v1/announcements/{publicId}", publicId))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST announcement accepts image and location for admin`() {
        `when`(announcementService.createAnnouncement(any())).thenReturn(announcement)

        mockMvc
            .perform(
                post("/api/v1/announcements")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "title": "여름 수련회",
                          "body": "수련회 안내",
                          "startAt": "2026-08-30T10:00:00Z",
                          "imageUrl": "https://cdn.example.org/retreat.jpg",
                          "location": "교회 본당",
                          "category": "EVENT"
                        }
                        """.trimIndent(),
                    ).with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isOk)

        val request = argumentCaptor<CreateAnnouncementRequest>()
        verify(announcementService).createAnnouncement(request.capture())
        assertEquals("https://cdn.example.org/retreat.jpg", request.firstValue.imageUrl)
        assertEquals("교회 본당", request.firstValue.location)
    }

    @Test
    fun `PUT announcement replaces image and location for admin`() {
        `when`(announcementService.updateAnnouncement(org.mockito.kotlin.eq(publicId), any())).thenReturn(announcement)

        mockMvc
            .perform(
                put("/api/v1/announcements/{publicId}", publicId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "title": "여름 수련회",
                          "body": "수정된 안내",
                          "startAt": "2026-08-30T10:00:00Z",
                          "imageUrl": "https://cdn.example.org/updated.jpg",
                          "location": "교육관",
                          "category": "EVENT"
                        }
                        """.trimIndent(),
                    ).with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isOk)

        val request = argumentCaptor<UpdateAnnouncementRequest>()
        verify(announcementService).updateAnnouncement(org.mockito.kotlin.eq(publicId), request.capture())
        assertEquals("https://cdn.example.org/updated.jpg", request.firstValue.imageUrl)
        assertEquals("교육관", request.firstValue.location)
    }
}
