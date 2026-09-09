package com.hanmaum.dn.app.features.verses.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.verses.api.v1.dto.DailyVerseResponse
import com.hanmaum.dn.app.features.verses.api.v1.dto.VerseReference
import com.hanmaum.dn.app.features.verses.api.v1.dto.WeeklyVerseResponse
import com.hanmaum.dn.app.features.verses.service.VerseService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import kotlin.test.Test

@WebMvcTest(VerseController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class VerseControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var verseService: VerseService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private fun memberToken() = jwt().jwt { it.subject("kc-001") }.authorities(SimpleGrantedAuthority("ROLE_MEMBER"))

    private fun adminToken() = jwt().jwt { it.subject("kc-admin") }.authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    @Test
    fun `GET today exposes the multilingual reference`() {
        `when`(verseService.getToday()).thenReturn(
            DailyVerseResponse(
                reference = VerseReference(ko = "신명기 3:1-11", en = "Deuteronomy 3:1-11", de = "5. Mose 3,1-11"),
                book = 5,
                chapter = 3,
                verseFrom = 1,
                verseTo = 11,
                translation = "개역개정",
                sourceUrl = "https://bible.asher.design/quiettime.php?qt_date=2026-09-08",
            ),
        )

        mockMvc
            .perform(get("/api/v1/verses/today").with(memberToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.reference.ko").value("신명기 3:1-11"))
            .andExpect(jsonPath("$.data.reference.en").value("Deuteronomy 3:1-11"))
            .andExpect(jsonPath("$.data.reference.de").value("5. Mose 3,1-11"))
            .andExpect(jsonPath("$.data.translation").value("개역개정"))
    }

    @Test
    fun `GET today serves an empty payload on a day without a passage`() {
        `when`(verseService.getToday()).thenReturn(DailyVerseResponse())

        // A gap in the plan. A 200 with nothing in it, not an error — the card has nothing
        // to show, and the client must be able to tell that apart from a 503.
        mockMvc
            .perform(get("/api/v1/verses/today").with(memberToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.reference").doesNotExist())
            .andExpect(jsonPath("$.data.notice").doesNotExist())
    }

    @Test
    fun `GET today carries the sunday notice as its own field`() {
        `when`(verseService.getToday()).thenReturn(DailyVerseResponse(notice = "주일 말씀!"))

        // The notice arrives separately from the reference, so a client renders it without
        // deriving the weekday itself — an empty payload also means "gap in the plan", and
        // guessing from the date would announce the Sunday service on a Tuesday.
        mockMvc
            .perform(get("/api/v1/verses/today").with(memberToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.notice").value("주일 말씀!"))
            .andExpect(jsonPath("$.data.reference").doesNotExist())
    }

    @Test
    fun `GET weekly carries the verse text`() {
        `when`(verseService.getWeekly()).thenReturn(
            WeeklyVerseResponse(
                reference = VerseReference(ko = "요한복음 1:5", en = "John 1:5", de = "Johannes 1,5"),
                book = 43,
                chapter = 1,
                verseFrom = 5,
                verseTo = 5,
                translation = "개역개정",
                text = "빛이 어둠에 비치되",
                weekStart = LocalDate.of(2026, 9, 6),
                weekEnd = LocalDate.of(2026, 9, 12),
            ),
        )

        mockMvc
            .perform(get("/api/v1/verses/weekly").with(memberToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.text").value("빛이 어둠에 비치되"))
            .andExpect(jsonPath("$.data.weekStart").value("2026-09-06"))
            .andExpect(jsonPath("$.data.weekEnd").value("2026-09-12"))
    }

    @Test
    fun `both cards require authentication`() {
        mockMvc.perform(get("/api/v1/verses/today")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/verses/weekly")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `PUT weekly is refused for a plain member`() {
        // The weekly selection is the only source there is for 주간 암송; letting any member
        // rewrite it would let any member rewrite the whole congregation's card.
        mockMvc
            .perform(
                put("/api/v1/verses/weekly")
                    .with(memberToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"book":43,"chapterStart":1,"verseStart":5,"chapterEnd":1,"verseEnd":5}"""),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `PUT weekly rejects a book outside the canon`() {
        mockMvc
            .perform(
                put("/api/v1/verses/weekly")
                    .with(adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"book":67,"chapterStart":1,"verseStart":5,"chapterEnd":1,"verseEnd":5}"""),
            ).andExpect(status().isBadRequest)
    }
}
