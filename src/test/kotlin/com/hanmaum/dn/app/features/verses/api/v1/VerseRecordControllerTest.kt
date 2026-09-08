package com.hanmaum.dn.app.features.verses.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.common.domainvalue.VerseRecordKind
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.verses.api.v1.dto.VerseRecordBlock
import com.hanmaum.dn.app.features.verses.api.v1.dto.VerseRecordsResponse
import com.hanmaum.dn.app.features.verses.service.VerseRecordService
import org.mockito.Mockito.`when`
import org.mockito.kotlin.eq
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import kotlin.test.Test

@WebMvcTest(VerseRecordController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class)
class VerseRecordControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var verseRecordService: VerseRecordService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val weekStart = LocalDate.of(2026, 9, 6)

    private fun memberToken() = jwt().jwt { it.subject("kc-001") }.authorities(SimpleGrantedAuthority("ROLE_MEMBER"))

    private fun block(
        marked: Boolean = true,
        markable: Boolean = true,
        total: Long = 84,
    ) = VerseRecordBlock(
        weekStart = weekStart,
        days = listOf(LocalDate.of(2026, 9, 7)),
        todayMarked = marked,
        todayMarkable = markable,
        totalDays = total,
    )

    @Test
    fun `GET records returns both streaks in one payload`() {
        `when`(verseRecordService.getRecords("kc-001")).thenReturn(
            VerseRecordsResponse(quietTime = block(total = 84), recitation = block(marked = false, total = 127)),
        )

        // One request for one screen: two calls would make Home wait twice for one card row.
        mockMvc
            .perform(get("/api/v1/verses/records").with(memberToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.quietTime.totalDays").value(84))
            .andExpect(jsonPath("$.data.quietTime.todayMarked").value(true))
            .andExpect(jsonPath("$.data.recitation.totalDays").value(127))
            .andExpect(jsonPath("$.data.recitation.todayMarked").value(false))
            .andExpect(jsonPath("$.data.quietTime.weekStart").value("2026-09-06"))
    }

    @Test
    fun `POST records marks today and answers 201`() {
        `when`(verseRecordService.mark(eq("kc-001"), eq(VerseRecordKind.QUIET_TIME))).thenReturn(block())

        mockMvc
            .perform(
                post("/api/v1/verses/records")
                    .with(memberToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"kind":"QUIET_TIME"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.todayMarked").value(true))
    }

    @Test
    fun `POST records rejects an unknown kind instead of guessing`() {
        mockMvc
            .perform(
                post("/api/v1/verses/records")
                    .with(memberToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"kind":"SOMETHING_ELSE"}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `the contract offers no way to take a mark back`() {
        // "No undo" is enforced by the operation not existing, not by trusting the client
        // not to call it. Spring answers 405 because the mapping is absent.
        mockMvc
            .perform(delete("/api/v1/verses/records").with(memberToken()))
            .andExpect(status().isMethodNotAllowed)
    }

    @Test
    fun `records require authentication`() {
        mockMvc.perform(get("/api/v1/verses/records")).andExpect(status().isUnauthorized)
    }
}
