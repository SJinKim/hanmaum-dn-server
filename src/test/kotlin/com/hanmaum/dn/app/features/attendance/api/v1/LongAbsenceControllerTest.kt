package com.hanmaum.dn.app.features.attendance.api.v1

import com.hanmaum.dn.app.common.config.SecurityConfig
import com.hanmaum.dn.app.common.security.Roles
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceChangeDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceChangePage
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceContactStatusRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceGroupDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceHistoryPage
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceMemoRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceMemoUpdate
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsencePhaseResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceReturnRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceReturnResponse
import com.hanmaum.dn.app.features.attendance.api.v1.dto.LongAbsenceSummaryResponse
import com.hanmaum.dn.app.features.attendance.domain.AbsenceChangeKind
import com.hanmaum.dn.app.features.attendance.domain.AbsenceChangeOrigin
import com.hanmaum.dn.app.features.attendance.domain.AbsencePhaseEndReason
import com.hanmaum.dn.app.features.attendance.service.LongAbsenceException
import com.hanmaum.dn.app.features.attendance.service.LongAbsenceHistoryService
import com.hanmaum.dn.app.features.attendance.service.LongAbsenceService
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@WebMvcTest(LongAbsenceController::class, excludeAutoConfiguration = [OAuth2ResourceServerAutoConfiguration::class])
@ActiveProfiles("test")
@Import(SecurityConfig::class, LongAbsenceExceptionHandler::class)
@ExtendWith(OutputCaptureExtension::class)
class LongAbsenceControllerTest {
    private companion object {
        const val RETURN_BODY = """{"lastSundayDate":"2026-07-26","version":0}"""
    }

    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var service: LongAbsenceService

    @MockitoBean private lateinit var history: LongAbsenceHistoryService

    @MockitoBean private lateinit var memberRepository: MemberRepository

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    private val memberId = UUID.randomUUID()
    private val phaseId = UUID.randomUUID()
    private val base = "/api/v1/attendance/long-absences"
    private val sentinel = "SENTINEL-우울증-375"

    private fun token(role: String) =
        jwt()
            .jwt { it.subject("kc-001") }
            .authorities(SimpleGrantedAuthority("ROLE_$role"))

    private fun row(memo: String? = null) =
        LongAbsenceResponse(
            memberPublicId = memberId,
            fullName = "홍길동",
            discriminator = null,
            group = LongAbsenceGroupDto(UUID.randomUUID(), "1순", "청년부"),
            phasePublicId = null,
            lastSundayDate = LocalDate.of(2026, 7, 26),
            absenceAnchorDate = LocalDate.of(2026, 7, 26),
            absentSince = LocalDate.of(2026, 7, 27),
            absenceDays = 92,
            absenceMonths = 3,
            postAssignmentAttendance = null,
            contacted = false,
            contactedBy = null,
            contactedByMember = null,
            contactedAt = null,
            memo = memo,
            version = 0,
        )

    private fun putMemo(
        body: String,
        role: String = Roles.PASTOR,
    ) = mockMvc.perform(
        put("$base/$memberId/memo").with(token(role)).contentType(MediaType.APPLICATION_JSON).content(body),
    )

    private fun putReturn(
        body: String,
        role: String = Roles.PASTOR,
    ) = mockMvc.perform(
        put("$base/$memberId/return").with(token(role)).contentType(MediaType.APPLICATION_JSON).content(body),
    )

    private fun putContact(
        body: String,
        role: String = Roles.ADMIN,
    ) = mockMvc.perform(
        put("$base/$memberId/contact-status").with(token(role)).contentType(MediaType.APPLICATION_JSON).content(body),
    )

    // ─── Access (AK 15) ─────────────────────────────────────────────────────

    @Test
    fun `without a token every route is a 401`() {
        mockMvc.perform(get(base)).andExpect(status().isUnauthorized)
        mockMvc.perform(get("$base/summary")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(put("$base/$memberId/memo").contentType(MediaType.APPLICATION_JSON).content("""{"memo":null}"""))
            .andExpect(status().isUnauthorized)
        mockMvc
            .perform(put("$base/$memberId/return").contentType(MediaType.APPLICATION_JSON).content(RETURN_BODY))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("$base/history")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("$base/$memberId/phases/$phaseId")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("$base/$memberId/phases/$phaseId/changes")).andExpect(status().isUnauthorized)
        verifyNoInteractions(service, history)
    }

    @Test
    fun `a token without a usable subject is a 401 on every write and reaches no service`() {
        listOf(" ", "\t").forEach { blankSubject ->
            val pastor = jwt().jwt { it.subject(blankSubject) }.authorities(SimpleGrantedAuthority("ROLE_${Roles.PASTOR}"))
            mockMvc
                .perform(
                    put(
                        "$base/$memberId/memo",
                    ).with(
                        pastor,
                    ).contentType(MediaType.APPLICATION_JSON)
                        .content("""{"lastSundayDate":"2026-07-26","version":0,"memo":"x"}"""),
                ).andExpect(status().isUnauthorized)
            mockMvc
                .perform(
                    put(
                        "$base/$memberId/contact-status",
                    ).with(
                        pastor,
                    ).contentType(MediaType.APPLICATION_JSON)
                        .content("""{"lastSundayDate":"2026-07-26","version":0,"contacted":true}"""),
                ).andExpect(status().isUnauthorized)
            mockMvc
                .perform(put("$base/$memberId/return").with(pastor).contentType(MediaType.APPLICATION_JSON).content(RETURN_BODY))
                .andExpect(status().isUnauthorized)
        }
        verifyNoInteractions(service, history)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            Roles.NOTE_TAKER, Roles.GROUP_LEADER, Roles.MINISTRY_LEADER, Roles.NEWCOMER_VIEWER, Roles.NEWCOMER_EDITOR,
            Roles.GENERAL_OPERATIONS_EDITOR, Roles.TRAINING_EDITOR, Roles.GUIDE_EDITOR,
        ],
    )
    fun `every other role is a 403 on reads and writes`(role: String) {
        mockMvc.perform(get(base).with(token(role))).andExpect(status().isForbidden)
        mockMvc.perform(get("$base/summary").with(token(role))).andExpect(status().isForbidden)
        putMemo("""{"lastSundayDate":"2026-07-26","version":0,"memo":"x"}""", role).andExpect(status().isForbidden)
        putContact("""{"lastSundayDate":"2026-07-26","version":0,"contacted":true}""", role).andExpect(status().isForbidden)
        putReturn(RETURN_BODY, role).andExpect(status().isForbidden)
        mockMvc.perform(get("$base/history").with(token(role))).andExpect(status().isForbidden)
        mockMvc.perform(get("$base/$memberId/phases/$phaseId").with(token(role))).andExpect(status().isForbidden)
        mockMvc.perform(get("$base/$memberId/phases/$phaseId/changes").with(token(role))).andExpect(status().isForbidden)
        verifyNoInteractions(service, history)
    }

    @ParameterizedTest
    @ValueSource(strings = [Roles.PASTOR, Roles.ADMIN])
    fun `pastor and admin get the list, uncached and without contact or internal data`(role: String) {
        `when`(service.list()).thenReturn(listOf(row()))

        mockMvc
            .perform(get(base).with(token(role)))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.data[0].memberPublicId").value(memberId.toString()))
            .andExpect(jsonPath("$.data[0].absenceMonths").value(3))
            .andExpect(jsonPath("$.data[0].version").value(0))
            .andExpect(jsonPath("$.data[0].memo").isEmpty)
            .andExpect(content().string(not(containsString("phone"))))
            .andExpect(content().string(not(containsString("\"id\""))))
    }

    @Test
    fun `empty list is a 200 with an empty array, summary is uncached`() {
        `when`(service.list()).thenReturn(emptyList())
        `when`(service.summary()).thenReturn(LongAbsenceSummaryResponse(LocalDate.of(2026, 10, 26), 0, 0, 0))

        mockMvc.perform(get(base).with(token(Roles.ADMIN))).andExpect(status().isOk).andExpect(jsonPath("$.data").isEmpty)
        mockMvc
            .perform(get("$base/summary").with(token(Roles.PASTOR)))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.data.atLeastSixMonthsCount").value(0))
    }

    // ─── Writes and validation (AK 11–13a) ──────────────────────────────────

    @Test
    fun `memo of exactly 2000 characters is accepted, explicit null clears`() {
        `when`(service.setMemo(eq(memberId), any(), any())).thenReturn(row("x"))
        val memo = "가".repeat(2000)

        putMemo("""{"lastSundayDate":"2026-07-26","version":1,"memo":"$memo"}""").andExpect(status().isOk)
        putMemo("""{"lastSundayDate":"2026-07-26","version":1,"memo":null}""").andExpect(status().isOk)

        val requests = argumentCaptor<LongAbsenceMemoRequest>()
        verify(service, org.mockito.Mockito.times(2)).setMemo(eq(memberId), requests.capture(), any())
        assertEquals(2000, requests.firstValue.memo?.length)
        assertNull(requests.secondValue.memo)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"lastSundayDate":"2026-07-26","version":1}""",
            """{"lastSundayDate":"2026-07-26","version":-1,"memo":"x"}""",
            """{"version":1,"memo":"x"}""",
            """{"lastSundayDate":"26.07.2026","version":1,"memo":"x"}""",
            """{"lastSundayDate":"2026-07-26","version":"one","memo":"x"}""",
        ],
    )
    fun `memo key missing, negative version or bad date is a 400 with a fixed code`(body: String) {
        putMemo(body)
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value(LongAbsenceException.INVALID_REQUEST))
        verifyNoInteractions(service)
    }

    @Test
    fun `a 2001 character memo is a 400 that neither echoes nor logs it`(output: CapturedOutput) {
        val memo = sentinel + "x".repeat(2001 - sentinel.length)

        putMemo("""{"lastSundayDate":"2026-07-26","version":1,"memo":"$memo"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(LongAbsenceException.INVALID_REQUEST))
            .andExpect(content().string(not(containsString(sentinel))))

        verifyNoInteractions(service)
        assertFalse(output.all.contains(sentinel))
    }

    @Test
    fun `broken JSON with a memo is a 400 and the memo stays out of the log`(output: CapturedOutput) {
        putMemo("""{"lastSundayDate":"2026-07-26","version":1,"memo":"$sentinel""")
            .andExpect(status().isBadRequest)
            .andExpect(content().string(not(containsString(sentinel))))
        putContact("""{"lastSundayDate":"2026-07-26","version":1,"contacted":true,"memoUpdate":{"value":["$sentinel"]}}""")
            .andExpect(status().isBadRequest)
            .andExpect(content().string(not(containsString(sentinel))))

        assertFalse(output.all.contains(sentinel))
    }

    @Test
    fun `an unexpected failure is a safe 500 without the memo in body or log`(output: CapturedOutput) {
        `when`(service.setMemo(eq(memberId), any(), any())).thenThrow(IllegalStateException("insert failed: $sentinel"))

        putMemo("""{"lastSundayDate":"2026-07-26","version":1,"memo":"$sentinel"}""")
            .andExpect(status().isInternalServerError)
            .andExpect(content().string(not(containsString(sentinel))))

        assertFalse(output.all.contains(sentinel))
    }

    @Test
    fun `contact status takes the actor from the token and passes the optional memo update`() {
        `when`(service.setContactStatus(eq(memberId), any(), eq("kc-001"))).thenReturn(row())

        putContact("""{"lastSundayDate":"2026-07-26","version":2,"contacted":false}""").andExpect(status().isOk)
        putContact("""{"lastSundayDate":"2026-07-26","version":2,"contacted":false,"memoUpdate":null}""").andExpect(status().isOk)
        putContact("""{"lastSundayDate":"2026-07-26","version":2,"contacted":false,"memoUpdate":{"value":null}}""").andExpect(status().isOk)
        putContact("""{"lastSundayDate":"2026-07-26","version":2,"contacted":true,"memoUpdate":{"value":"새 소식"}}""")
            .andExpect(status().isOk)

        val requests = argumentCaptor<LongAbsenceContactStatusRequest>()
        verify(service, org.mockito.Mockito.times(4)).setContactStatus(eq(memberId), requests.capture(), eq("kc-001"))
        assertNull(requests.allValues[0].memoUpdate)
        assertNull(requests.allValues[1].memoUpdate)
        assertEquals(LongAbsenceMemoUpdate(null), requests.allValues[2].memoUpdate)
        assertEquals("새 소식", requests.allValues[3].memoUpdate?.value)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"lastSundayDate":"2026-07-26","version":2,"contacted":true,"memoUpdate":{}}""",
            """{"lastSundayDate":"2026-07-26","version":2,"contacted":true,"memoUpdate":"text"}""",
            """{"lastSundayDate":"2026-07-26","version":2}""",
        ],
    )
    fun `an empty memoUpdate, a wrong type or a missing status is a 400`(body: String) {
        putContact(body).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value(LongAbsenceException.INVALID_REQUEST))
        verifyNoInteractions(service)
    }

    @Test
    fun `a memoUpdate longer than 2000 is a 400`() {
        putContact("""{"lastSundayDate":"2026-07-26","version":2,"contacted":true,"memoUpdate":{"value":"${"x".repeat(2001)}"}}""")
            .andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }

    @Test
    fun `invalid member id is a 400`() {
        mockMvc
            .perform(
                put("$base/not-a-uuid/memo")
                    .with(token(Roles.ADMIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"lastSundayDate":"2026-07-26","version":1,"memo":"x"}"""),
            ).andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }

    @Test
    fun `domain rejections map to 404 and 409 with their codes`() {
        `when`(service.setMemo(eq(memberId), any(), any()))
            .thenThrow(LongAbsenceException.memberNotFound())
            .thenThrow(LongAbsenceException.notCurrent())
            .thenThrow(LongAbsenceException.versionConflict())
        val body = """{"lastSundayDate":"2026-07-26","version":1,"memo":"x"}"""

        putMemo(body).andExpect(status().isNotFound)
        putMemo(body).andExpect(status().isConflict).andExpect(jsonPath("$.code").value(LongAbsenceException.NOT_CURRENT))
        putMemo(body).andExpect(status().isConflict).andExpect(jsonPath("$.code").value(LongAbsenceException.VERSION_CONFLICT))
    }

    // ─── Return and history (HDN-379) ───────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = [Roles.PASTOR, Roles.ADMIN])
    fun `manual return takes the actor from the token, never from the body`(role: String) {
        `when`(service.markReturned(eq(memberId), any(), eq("kc-001"))).thenReturn(
            LongAbsenceReturnResponse(
                memberId,
                phaseId,
                LocalDate.of(2026, 10, 26),
                AbsencePhaseEndReason.MANUAL_RETURN,
                Instant.EPOCH,
                "kc-001",
                null,
                7,
            ),
        )

        putReturn("""{"lastSundayDate":"2026-07-26","version":6,"returnedBy":"kc-evil","endedOn":"2020-01-01"}""", role)
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.data.phasePublicId").value(phaseId.toString()))
            .andExpect(jsonPath("$.data.endReason").value("MANUAL_RETURN"))
            .andExpect(jsonPath("$.data.returnedBy").value("kc-001"))
            .andExpect(jsonPath("$.data.version").value(7))

        val requests = argumentCaptor<LongAbsenceReturnRequest>()
        verify(service).markReturned(eq(memberId), requests.capture(), eq("kc-001"))
        assertEquals(LongAbsenceReturnRequest(LocalDate.of(2026, 7, 26), 6), requests.firstValue)
    }

    @ParameterizedTest
    @ValueSource(strings = ["""{"version":1}""", """{"lastSundayDate":"2026-07-26"}""", """{"lastSundayDate":"2026-07-26","version":-1}"""])
    fun `a return without date or version is a 400`(body: String) {
        putReturn(body).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value(LongAbsenceException.INVALID_REQUEST))
        verifyNoInteractions(service)
    }

    @Test
    fun `history defaults to page 0 of 20 and passes the member filter, uncached`() {
        `when`(history.history(null, 0, 20)).thenReturn(LongAbsenceHistoryPage(emptyList(), 0, 20, 0, 0))
        `when`(history.history(memberId, 2, 100)).thenReturn(LongAbsenceHistoryPage(emptyList(), 2, 100, 3, 1))

        mockMvc
            .perform(get("$base/history").with(token(Roles.PASTOR)))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            .andExpect(jsonPath("$.data.items").isEmpty)
            .andExpect(jsonPath("$.data.size").value(20))
        mockMvc
            .perform(
                get("$base/history")
                    .param("page", "2")
                    .param("size", "100")
                    .param("memberPublicId", "$memberId")
                    .with(token(Roles.ADMIN)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalElements").value(3))
    }

    @ParameterizedTest
    @ValueSource(strings = ["page=-1", "size=0", "size=101", "page=2147483648", "page=x", "memberPublicId=not-a-uuid"])
    fun `invalid paging or member id is a 400 with the long-absence code`(query: String) {
        mockMvc
            .perform(get("$base/history?$query").with(token(Roles.PASTOR)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(LongAbsenceException.INVALID_REQUEST))
            .andExpect(header().string("Cache-Control", containsString("no-store")))
        // The change page has no member filter; its member id is in the path.
        val changes =
            if (query.startsWith(
                    "memberPublicId",
                )
            ) {
                "$base/not-a-uuid/phases/$phaseId/changes"
            } else {
                "$base/$memberId/phases/$phaseId/changes?$query"
            }
        mockMvc
            .perform(get(changes).with(token(Roles.PASTOR)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value(LongAbsenceException.INVALID_REQUEST))
        verifyNoInteractions(history)
    }

    @Test
    fun `phase and changes are bound to both ids and unknown ones are 404 with their codes`() {
        `when`(history.phase(memberId, phaseId))
            .thenThrow(LongAbsenceException.memberNotFound())
            .thenThrow(LongAbsenceException.phaseNotFound())
        `when`(history.changes(memberId, phaseId, 0, 20)).thenReturn(LongAbsenceChangePage(emptyList(), 0, 20, 0, 0))

        mockMvc
            .perform(get("$base/$memberId/phases/$phaseId").with(token(Roles.PASTOR)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value(LongAbsenceException.MEMBER_NOT_FOUND))
        mockMvc
            .perform(get("$base/$memberId/phases/$phaseId").with(token(Roles.PASTOR)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value(LongAbsenceException.PHASE_NOT_FOUND))
        mockMvc
            .perform(get("$base/$memberId/phases/$phaseId/changes").with(token(Roles.ADMIN)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalPages").value(0))
        mockMvc.perform(get("$base/$memberId/phases/not-a-uuid").with(token(Roles.ADMIN))).andExpect(status().isBadRequest)
    }

    @Test
    fun `memo versions in a change page never reach the log on a failure`(output: CapturedOutput) {
        `when`(history.changes(memberId, phaseId, 0, 20)).thenThrow(IllegalStateException("decrypt failed: $sentinel"))

        mockMvc
            .perform(get("$base/$memberId/phases/$phaseId/changes").with(token(Roles.ADMIN)))
            .andExpect(status().isInternalServerError)
            .andExpect(content().string(not(containsString(sentinel))))

        assertFalse(output.all.contains(sentinel))
    }

    @Test
    fun `request and response types never print the memo`() {
        val text = "$sentinel ${Instant.EPOCH}"
        val strings =
            listOf(
                LongAbsenceMemoRequest(LocalDate.EPOCH, 0, text).toString(),
                LongAbsenceContactStatusRequest(LocalDate.EPOCH, 0, true, LongAbsenceMemoUpdate(text)).toString(),
                row(text).toString(),
                LongAbsenceChangeDto(
                    phaseId,
                    1,
                    AbsenceChangeKind.MEMO_CHANGED,
                    AbsenceChangeOrigin.ACTION,
                    Instant.EPOCH,
                    text,
                    null,
                    null,
                    text,
                ).toString(),
                LongAbsenceChangePage(listOf(), 0, 20, 0, 0).toString(),
                LongAbsencePhaseResponse(
                    phaseId,
                    memberId,
                    "홍길동",
                    null,
                    LocalDate.EPOCH,
                    LocalDate.EPOCH,
                    LocalDate.EPOCH,
                    null,
                    null,
                    true,
                    text,
                    null,
                    null,
                    null,
                    text,
                    null,
                    text,
                    true,
                ).toString(),
            )
        strings.forEach { assertFalse(it.contains(sentinel), it) }
    }
}
