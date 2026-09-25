package com.hanmaum.dn.app.features.ministry.api.v1

import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.api.v1.dto.CreateMinistryRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryContactDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryScheduleDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.UpdateMinistryRequest
import com.hanmaum.dn.app.features.ministry.service.MinistryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test

@WebMvcTest(MinistryController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
class MinistryControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var ministryService: MinistryService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var memberRepository: MemberRepository

    @Test
    fun `POST ministry accepts schedule location`() {
        val body =
            """{"title":"찬양팀","subtitle":"찬양 사역","about":"소개","schedules":[""" +
                """{"description":"연습","startTime":"07:00","endTime":"09:00","location":"본당","dayOfWeek":"THURSDAY"}]}"""
        val dto =
            MinistryDto(
                "id",
                "찬양팀",
                "찬양 사역",
                "소개",
                emptyList(),
                listOf(MinistryScheduleDto("연습", LocalTime.of(7, 0), LocalTime.of(9, 0), "본당", DayOfWeek.THURSDAY)),
                emptyList(),
                null,
                true,
            )
        `when`(ministryService.createMinistry(any())).thenReturn(dto)

        mockMvc
            .perform(
                post("/api/v1/ministries")
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.schedules[0].location").value("본당"))
            .andExpect(jsonPath("$.data.schedules[0].dayOfWeek").value("THURSDAY"))

        val request = argumentCaptor<CreateMinistryRequest>()
        verify(ministryService).createMinistry(request.capture())
        assertEquals(
            "본당",
            request.firstValue.schedules
                .single()
                .location,
        )
        assertEquals(
            DayOfWeek.THURSDAY,
            request.firstValue.schedules
                .single()
                .dayOfWeek,
        )
    }

    @Test
    fun `PATCH ministry accepts schedule location`() {
        val publicId = UUID.randomUUID()
        val body = """{"schedules":[{"description":"연습","startTime":"07:00","endTime":"09:00","location":"3층","dayOfWeek":"SUNDAY"}]}"""
        val dto =
            MinistryDto(
                publicId.toString(),
                "찬양팀",
                "찬양 사역",
                "소개",
                emptyList(),
                listOf(MinistryScheduleDto("연습", LocalTime.of(7, 0), LocalTime.of(9, 0), "3층", DayOfWeek.SUNDAY)),
                emptyList(),
                null,
                true,
            )
        `when`(ministryService.updateMinistry(eq(publicId), any())).thenReturn(dto)

        mockMvc
            .perform(
                patch(
                    "/api/v1/ministries/{publicId}",
                    publicId,
                ).with(user("admin").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.schedules[0].location").value("3층"))
            .andExpect(jsonPath("$.data.schedules[0].dayOfWeek").value("SUNDAY"))

        val request = argumentCaptor<UpdateMinistryRequest>()
        verify(ministryService).updateMinistry(eq(publicId), request.capture())
        assertEquals(
            "3층",
            request.firstValue.schedules!!
                .single()
                .location,
        )
        assertEquals(
            DayOfWeek.SUNDAY,
            request.firstValue.schedules!!
                .single()
                .dayOfWeek,
        )
    }

    @Test
    fun `POST ministry rejects unknown schedule weekday`() {
        val body =
            """{"title":"찬양팀","subtitle":"찬양 사역","about":"소개","schedules":[""" +
                """{"description":"연습","startTime":"07:00","endTime":"09:00","dayOfWeek":"FUNDAY"}]}"""

        mockMvc
            .perform(
                post("/api/v1/ministries")
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isBadRequest)
        org.mockito.Mockito.verifyNoInteractions(ministryService)
    }

    @Test
    fun `POST ministry rejects schedule location longer than 100 characters`() {
        val location = "가".repeat(101)
        val body =
            """{"title":"찬양팀","subtitle":"찬양 사역","about":"소개","schedules":[""" +
                """{"description":"연습","startTime":"07:00","endTime":"09:00","location":"$location"}]}"""

        mockMvc
            .perform(
                post("/api/v1/ministries")
                    .with(user("admin").roles("ADMIN"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isBadRequest)
        org.mockito.Mockito.verifyNoInteractions(ministryService)
    }

    @Test
    fun `GET ministry detail returns all backend-driven page fields`() {
        val publicId = UUID.fromString("3f2a1b4c-0000-0000-0000-000000000001")
        `when`(ministryService.getMinistry(publicId)).thenReturn(
            MinistryDto(
                publicId = publicId.toString(),
                title = "난민 사역",
                subtitle = "하나님의 사랑을 나누고, 복음을 전하는 사역입니다.",
                about = "한 달에 한 번 난민들을 섬기는 사역입니다.",
                requirements =
                    listOf(
                        "큐베세 양육 수료자 + 일대일 양육 신청자",
                        "아이들을 섬기려는 마음이 있으신 분",
                    ),
                schedules =
                    listOf(
                        MinistryScheduleDto(
                            description = "매달 넷째 주 토요일: 새벽기도 후 준비모임",
                            startTime = LocalTime.of(7, 0),
                            endTime = LocalTime.of(9, 0),
                            location = "본당",
                            dayOfWeek = DayOfWeek.SATURDAY,
                        ),
                    ),
                contacts =
                    listOf(
                        MinistryContactDto(role = "팀장", name = "김영원 권사님"),
                        MinistryContactDto(role = "간사", name = "최혜령 자매님"),
                    ),
                imageUrl = null,
                isActive = true,
            ),
        )

        mockMvc
            .perform(
                get("/api/v1/ministries/{publicId}", publicId)
                    .with(user("member")),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.title").value("난민 사역"))
            .andExpect(jsonPath("$.data.subtitle").value("하나님의 사랑을 나누고, 복음을 전하는 사역입니다."))
            .andExpect(jsonPath("$.data.about").value("한 달에 한 번 난민들을 섬기는 사역입니다."))
            .andExpect(jsonPath("$.data.requirements[0]").value("큐베세 양육 수료자 + 일대일 양육 신청자"))
            .andExpect(jsonPath("$.data.schedules[0].description").value("매달 넷째 주 토요일: 새벽기도 후 준비모임"))
            .andExpect(jsonPath("$.data.schedules[0].startTime").value("07:00"))
            .andExpect(jsonPath("$.data.schedules[0].endTime").value("09:00"))
            .andExpect(jsonPath("$.data.schedules[0].location").value("본당"))
            .andExpect(jsonPath("$.data.schedules[0].dayOfWeek").value("SATURDAY"))
            .andExpect(jsonPath("$.data.contacts[0].role").value("팀장"))
            .andExpect(jsonPath("$.data.contacts[0].name").value("김영원 권사님"))
            .andExpect(jsonPath("$.data.contacts[1].role").value("간사"))
            .andExpect(jsonPath("$.data.contacts[1].name").value("최혜령 자매님"))
    }
}
