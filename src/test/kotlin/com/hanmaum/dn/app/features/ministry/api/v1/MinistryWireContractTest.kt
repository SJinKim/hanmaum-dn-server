package com.hanmaum.dn.app.features.ministry.api.v1

import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryContactDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryScheduleDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistrySummaryDto
import com.hanmaum.dn.app.features.ministry.service.MinistryService
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test

/**
 * Pins the JSON field names of the ministry endpoints.
 *
 * HDN-118: the 사역 list in the app was empty because the DTO names and the client's
 * expectations had drifted apart. All three sides now agree on `title` / `subtitle` /
 * `about` / `active`, and the dashboard writes those names back on create and update —
 * so the names are a contract, not an implementation detail.
 *
 * The detail *values* are covered by [MinistryControllerTest]. This file only asserts
 * shape, including the two things nothing else would catch:
 *
 *  - the list endpoint, which is the one that actually broke and had no coverage at all;
 *  - the exact spelling of `isActive`. Two artifacts still claim the server sends `active`:
 *    the generated spec in hanmaum-dn-ops, and the mobile app's two ministry wire models.
 *    Both are wrong — the server sends `isActive`, as the mobile app's own
 *    AttendanceDefinitionResponse already assumes. Asserting that `active` is absent keeps
 *    anyone from "fixing" the server to match the stale artifacts.
 */
@WebMvcTest(MinistryController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
class MinistryWireContractTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var ministryService: MinistryService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var memberRepository: MemberRepository

    private val publicId = UUID.fromString("3f2a1b4c-0000-0000-0000-000000000001")

    private val contacts =
        listOf(
            MinistryContactDto(role = "팀장", name = "김영원 권사님"),
            MinistryContactDto(role = "간사", name = "최혜령 자매님"),
        )

    private fun summary() =
        MinistrySummaryDto(
            publicId = publicId.toString(),
            title = "난민 사역",
            subtitle = "하나님의 사랑을 나누고, 복음을 전하는 사역입니다.",
            imageUrl = null,
            contacts = contacts,
            isActive = true,
        )

    private fun detail() =
        MinistryDto(
            publicId = publicId.toString(),
            title = "난민 사역",
            subtitle = "하나님의 사랑을 나누고, 복음을 전하는 사역입니다.",
            about = "한 달에 한 번 난민들을 섬기는 사역입니다.",
            requirements = listOf("큐베세 양육 수료자 + 일대일 양육 신청자"),
            schedules =
                listOf(
                    MinistryScheduleDto(
                        description = "매달 넷째 주 토요일: 새벽기도 후 준비모임",
                        startTime = LocalTime.of(7, 0),
                        endTime = LocalTime.of(9, 0),
                    ),
                ),
            contacts = contacts,
            imageUrl = null,
            isActive = true,
        )

    // ─── GET /ministries ──────────────────────────────────────────────────────

    @Test
    fun `the list entry carries exactly the field names both clients read`() {
        `when`(ministryService.getMinistries(null)).thenReturn(listOf(summary()))

        mockMvc
            .perform(get("/api/v1/ministries").with(user("member")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].publicId").exists())
            .andExpect(jsonPath("$.data[0].title").value("난민 사역"))
            .andExpect(jsonPath("$.data[0].subtitle").value("하나님의 사랑을 나누고, 복음을 전하는 사역입니다."))
            .andExpect(jsonPath("$.data[0].contacts").isArray)
            // The entity column names must never surface: these are what the client used
            // to expect, and what an "align the DTO with the entity" refactor would produce.
            .andExpect(jsonPath("$.data[0].name").doesNotExist())
            .andExpect(jsonPath("$.data[0].shortDescription").doesNotExist())
            .andExpect(jsonPath("$.data[0].longDescription").doesNotExist())
    }

    @Test
    fun `the list entry spells the flag isActive, not active`() {
        `when`(ministryService.getMinistries(null)).thenReturn(listOf(summary()))

        mockMvc
            .perform(get("/api/v1/ministries").with(user("member")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].isActive").value(true))
            .andExpect(jsonPath("$.data[0].active").doesNotExist())
    }

    // ─── GET /ministries/{publicId} ───────────────────────────────────────────

    @Test
    fun `the detail entry carries exactly the field names both clients read`() {
        `when`(ministryService.getMinistry(publicId)).thenReturn(detail())

        mockMvc
            .perform(get("/api/v1/ministries/{publicId}", publicId).with(user("member")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.title").exists())
            .andExpect(jsonPath("$.data.subtitle").exists())
            .andExpect(jsonPath("$.data.about").exists())
            .andExpect(jsonPath("$.data.requirements").isArray)
            .andExpect(jsonPath("$.data.schedules").isArray)
            .andExpect(jsonPath("$.data.name").doesNotExist())
            .andExpect(jsonPath("$.data.shortDescription").doesNotExist())
            .andExpect(jsonPath("$.data.longDescription").doesNotExist())
    }

    @Test
    fun `the detail entry spells the flag isActive, not active`() {
        `when`(ministryService.getMinistry(publicId)).thenReturn(detail())

        mockMvc
            .perform(get("/api/v1/ministries/{publicId}", publicId).with(user("member")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isActive").value(true))
            .andExpect(jsonPath("$.data.active").doesNotExist())
    }

    // ─── Contact ordering ─────────────────────────────────────────────────────

    @Test
    fun `contacts keep the order the admin arranged, so the first one is the leader`() {
        `when`(ministryService.getMinistry(publicId)).thenReturn(detail())
        `when`(ministryService.getMinistries(null)).thenReturn(listOf(summary()))

        // Both clients render contacts[0] as 리더. There is no leader field — roles vary
        // per ministry — so the ordering is what makes that reliable, on both endpoints.
        mockMvc
            .perform(get("/api/v1/ministries/{publicId}", publicId).with(user("member")))
            .andExpect(jsonPath("$.data.contacts[0].role").value("팀장"))
            .andExpect(jsonPath("$.data.contacts[0].name").value("김영원 권사님"))
            .andExpect(jsonPath("$.data.contacts[1].role").value("간사"))

        mockMvc
            .perform(get("/api/v1/ministries").with(user("member")))
            .andExpect(jsonPath("$.data[0].contacts[0].role").value("팀장"))
            .andExpect(jsonPath("$.data[0].contacts[0].name").value("김영원 권사님"))
    }
}
