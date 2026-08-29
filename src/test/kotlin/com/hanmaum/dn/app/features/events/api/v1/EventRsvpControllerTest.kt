package com.hanmaum.dn.app.features.events.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.events.api.v1.dto.ActiveEventRsvpDto
import com.hanmaum.dn.app.features.events.api.v1.dto.EventAttendeesResponse
import com.hanmaum.dn.app.features.events.api.v1.dto.EventCheckInResponse
import com.hanmaum.dn.app.features.events.api.v1.dto.EventRsvpDto
import com.hanmaum.dn.app.features.events.api.v1.dto.EventRsvpResponseDto
import com.hanmaum.dn.app.features.events.domain.RsvpStatus
import com.hanmaum.dn.app.features.events.service.EventRsvpService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.mockito.Mockito.`when`
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test

@WebMvcTest(EventRsvpController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class EventRsvpControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var eventRsvpService: EventRsvpService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val now = OffsetDateTime.of(2026, 7, 12, 10, 0, 0, 0, ZoneOffset.ofHours(2))
    private val serializedNow = "2026-07-12T10:00:00+02:00"
    private val rsvpId = UUID.randomUUID()

    private fun sampleRsvpDto() =
        EventRsvpDto(
            publicId = rsvpId.toString(),
            title = "여름 수련회",
            windowStart = now.minusHours(1),
            windowEnd = now.plusHours(2),
            isActive = true,
            announcementPublicId = null,
        )

    @Test
    fun `POST events-rsvps returns 201 for admin`() {
        `when`(eventRsvpService.createRsvp(any())).thenReturn(sampleRsvpDto())

        mockMvc
            .perform(
                post("/api/v1/events/rsvps")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"여름 수련회","windowStart":"${now.minusHours(1)}","windowEnd":"${now.plusHours(2)}"}""")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.title").value("여름 수련회"))
    }

    @Test
    fun `POST events-rsvps returns 201 for group leader`() {
        `when`(eventRsvpService.createRsvp(any())).thenReturn(sampleRsvpDto())

        mockMvc
            .perform(
                post("/api/v1/events/rsvps")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"여름 수련회","windowStart":"${now.minusHours(1)}","windowEnd":"${now.plusHours(2)}"}""")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_GROUP_LEADER"))),
            ).andExpect(status().isCreated)
    }

    @Test
    fun `POST events-rsvps returns 403 for plain member`() {
        mockMvc
            .perform(
                post("/api/v1/events/rsvps")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"여름 수련회","windowStart":"${now.minusHours(1)}","windowEnd":"${now.plusHours(2)}"}""")
                    .with(jwt()),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `GET events-rsvps-active returns list for authenticated member`() {
        val announcementId = UUID.randomUUID()
        `when`(eventRsvpService.getActiveRsvps("kc-001")).thenReturn(
            listOf(
                ActiveEventRsvpDto(
                    rsvpId.toString(),
                    "여름 수련회",
                    now.minusHours(1),
                    now.plusHours(2),
                    announcementId,
                    RsvpStatus.MAYBE,
                    now,
                ),
                ActiveEventRsvpDto(
                    UUID.randomUUID().toString(),
                    "독립 행사",
                    now.minusHours(1),
                    now.plusHours(2),
                    null,
                    null,
                    null,
                ),
            ),
        )

        mockMvc
            .perform(
                get("/api/v1/events/rsvps/active").with(jwt().jwt { it.subject("kc-001") }),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].title").value("여름 수련회"))
            .andExpect(jsonPath("$.data[0].publicId").value(rsvpId.toString()))
            .andExpect(jsonPath("$.data[0].announcementId").value(announcementId.toString()))
            .andExpect(jsonPath("$.data[0].myStatus").value("MAYBE"))
            .andExpect(jsonPath("$.data[0].respondedAt").value(serializedNow))
            .andExpect(jsonPath("$.data[1].announcementId").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data[1].myStatus").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data[1].respondedAt").value(org.hamcrest.Matchers.nullValue()))
    }

    @Test
    fun `POST check-in returns 201 for authenticated member`() {
        `when`(eventRsvpService.checkIn(eq(rsvpId), any())).thenReturn(
            EventCheckInResponse(rsvpId.toString(), "여름 수련회", now),
        )

        mockMvc
            .perform(
                post("/api/v1/events/rsvps/$rsvpId/check-in")
                    .with(jwt().jwt { it.subject("kc-001") }),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.eventPublicId").value(rsvpId.toString()))
    }

    @Test
    fun `PUT response accepts every RSVP status`() {
        RsvpStatus.entries.forEach { rsvpStatus ->
            `when`(eventRsvpService.setResponse(rsvpId, "kc-001", rsvpStatus)).thenReturn(
                EventRsvpResponseDto(rsvpId.toString(), "여름 수련회", rsvpStatus, now),
            )

            mockMvc
                .perform(
                    put("/api/v1/events/rsvps/$rsvpId/response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"status":"$rsvpStatus"}""")
                        .with(jwt().jwt { it.subject("kc-001") }),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.status").value(rsvpStatus.name))
                .andExpect(jsonPath("$.data.respondedAt").value(serializedNow))
        }
    }

    @Test
    fun `PUT response returns 400 outside response window`() {
        `when`(eventRsvpService.setResponse(rsvpId, "kc-001", RsvpStatus.GOING)).thenThrow(
            org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "현재 RSVP 신청 기간이 아닙니다.",
            ),
        )

        mockMvc
            .perform(
                put("/api/v1/events/rsvps/$rsvpId/response")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"GOING"}""")
                    .with(jwt().jwt { it.subject("kc-001") }),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("현재 RSVP 신청 기간이 아닙니다."))
    }

    @Test
    fun `PUT response returns 401 without token`() {
        mockMvc
            .perform(
                put("/api/v1/events/rsvps/$rsvpId/response")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"GOING"}"""),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET attendees returns 200 for admin`() {
        `when`(eventRsvpService.getAttendees(rsvpId)).thenReturn(
            EventAttendeesResponse(
                eventPublicId = rsvpId.toString(),
                eventTitle = "여름 수련회",
                totalCount = 3,
                goingCount = 1,
                notGoingCount = 1,
                maybeCount = 1,
                attendees = emptyList(),
            ),
        )

        mockMvc
            .perform(
                get("/api/v1/events/rsvps/$rsvpId/attendees")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(3))
            .andExpect(jsonPath("$.data.goingCount").value(1))
            .andExpect(jsonPath("$.data.notGoingCount").value(1))
            .andExpect(jsonPath("$.data.maybeCount").value(1))
    }

    @Test
    fun `GET attendees returns 403 for plain member`() {
        mockMvc
            .perform(
                get("/api/v1/events/rsvps/$rsvpId/attendees").with(jwt()),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `DELETE deactivates and returns 204 for admin`() {
        mockMvc
            .perform(
                delete("/api/v1/events/rsvps/$rsvpId")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"))),
            ).andExpect(status().isNoContent)
    }

    @Test
    fun `PATCH update returns 200 for group leader`() {
        `when`(eventRsvpService.updateRsvp(eq(rsvpId), any())).thenReturn(sampleRsvpDto())

        mockMvc
            .perform(
                patch("/api/v1/events/rsvps/$rsvpId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"수정된 이름"}""")
                    .with(jwt().authorities(SimpleGrantedAuthority("ROLE_GROUP_LEADER"))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.title").value("여름 수련회"))
    }

    // ─── Helper: Mockito any() for non-nullable Kotlin params ─────────────────
    private fun <T> any(): T = org.mockito.kotlin.any()

    private fun <T> eq(value: T): T = org.mockito.kotlin.eq(value)
}
