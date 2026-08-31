package com.hanmaum.dn.app.common.api

import com.hanmaum.dn.app.features.announcements.api.v1.dto.AnnouncementDto
import com.hanmaum.dn.app.features.announcements.api.v1.dto.CreateAnnouncementRequest
import com.hanmaum.dn.app.features.announcements.api.v1.dto.UpdateAnnouncementRequest
import com.hanmaum.dn.app.features.attendance.api.v1.dto.DefinitionDto
import com.hanmaum.dn.app.features.attendance.api.v1.dto.UpdateDefinitionRequest
import com.hanmaum.dn.app.features.carpool.api.v1.dto.CarDto
import com.hanmaum.dn.app.features.events.api.v1.dto.EventRsvpDto
import com.hanmaum.dn.app.features.events.api.v1.dto.UpdateEventRsvpRequest
import com.hanmaum.dn.app.features.groups.api.v1.dto.ReportEntry
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberDto
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberSummaryDto
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.MinistrySummaryDto
import com.hanmaum.dn.app.features.ministry.api.v1.dto.UpdateMinistryRequest
import com.hanmaum.dn.app.features.training.api.v1.dto.TrainingCatalogDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.test.Test

/**
 * Pins the JSON names of every `is`-prefixed boolean in a DTO.
 *
 * These names are not written anywhere: Jackson derives them from the Kotlin property, and
 * springdoc derives them from the Java getter — which drops the `is` and yields a *different*
 * name. `api/openapi.yaml` therefore documented `active` while the server sent `isActive`,
 * for nineteen properties across eight DTOs (hanmaum-dn-ops#11).
 *
 * That is not cosmetic. hanmaum-dn-mobile-app#129 is exactly this: a client modelled the
 * field from the spec, it never bound, and a default masked the mismatch instead of failing.
 *
 * Every property below therefore carries an explicit `@JsonProperty`, and this test holds
 * the wire to it — in both directions, so annotating cannot quietly break request binding.
 */
class BooleanWireNameTest {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    private fun namesOf(value: Any): Set<String> = mapper.readTree(mapper.writeValueAsString(value)).propertyNames().toSet()

    // ─── Response DTOs: the name the clients read ─────────────────────────────

    @TestFactory
    fun `response dtos keep the is prefix on the wire`(): List<DynamicTest> {
        val cases: List<Pair<Any, String>> =
            listOf(
                definitionDto() to "isActive",
                carDto() to "isFull",
                carDto() to "isJoinedByMe",
                ministrySummaryDto() to "isActive",
                ministryDto() to "isActive",
                trainingCatalogDto() to "isActive",
                announcementDto() to "isPinned",
                reportEntry() to "isPresent",
                memberDto() to "isGroupLeader",
                memberDto() to "isNextGroupLeader",
                memberSummaryDto() to "isGroupLeader",
                memberSummaryDto() to "isNextGroupLeader",
                eventRsvpDto() to "isActive",
            )
        return cases.map { (dto, expected) ->
            DynamicTest.dynamicTest("${dto::class.simpleName}.$expected") {
                val names = namesOf(dto)
                assertTrue(expected in names, "expected `$expected` on the wire, got $names")
                // The springdoc spelling must never appear — that is the bug.
                val stripped = expected.removePrefix("is").replaceFirstChar { it.lowercase() }
                assertTrue(stripped !in names, "`$stripped` leaked onto the wire alongside `$expected`")
            }
        }
    }

    // ─── Request DTOs: the name the clients send ──────────────────────────────

    @TestFactory
    fun `request dtos bind the is prefixed name`(): List<DynamicTest> {
        val cases: List<Triple<String, Class<*>, (Any) -> Boolean?>> =
            listOf(
                Triple("""{"isActive":true}""", UpdateDefinitionRequest::class.java) { (it as UpdateDefinitionRequest).isActive },
                Triple("""{"isActive":true}""", UpdateMinistryRequest::class.java) { (it as UpdateMinistryRequest).isActive },
                Triple("""{"isActive":true}""", UpdateEventRsvpRequest::class.java) { (it as UpdateEventRsvpRequest).isActive },
                // Carries every required field: this DTO replaces rather than patches.
                Triple(
                    """{"title":"제목","body":"내용","startAt":"2026-08-30T10:00:00+02:00","category":"NOTICE","isPinned":true}""",
                    UpdateAnnouncementRequest::class.java,
                ) { (it as UpdateAnnouncementRequest).isPinned },
                Triple(
                    """{"isNextGroupLeader":true}""",
                    UpdateMemberRequest::class.java,
                ) { (it as UpdateMemberRequest).isNextGroupLeader },
            )
        return cases.map { (json, type, read) ->
            DynamicTest.dynamicTest("${type.simpleName} binds its is-prefixed flag") {
                assertEquals(true, read(mapper.readValue(json, type)))
            }
        }
    }

    @Test
    fun `a create request keeps the is prefixed name in both directions`() {
        val request = CreateAnnouncementRequest(title = "제목", body = "내용", category = "NOTICE", isPinned = true)

        assertTrue("isPinned" in namesOf(request))
        val parsed =
            mapper.readValue(
                """{"title":"제목","body":"내용","category":"NOTICE","isPinned":true}""",
                CreateAnnouncementRequest::class.java,
            )
        assertEquals(true, parsed.isPinned)
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private fun definitionDto() =
        DefinitionDto(
            publicId = "p",
            title = "주일예배",
            dayOfWeek = java.time.DayOfWeek.SUNDAY,
            windowStart = LocalTime.of(10, 0),
            windowEnd = LocalTime.of(12, 0),
            isActive = true,
        )

    private fun carDto() =
        CarDto(
            id = 1L,
            driverName = "김철수",
            carName = null,
            maxSeats = 4,
            currentPassengers = 2,
            departureLocation = null,
            departureTime = null,
            isFull = false,
            isJoinedByMe = true,
        )

    private fun ministrySummaryDto() =
        MinistrySummaryDto(publicId = "p", title = "t", subtitle = "s", imageUrl = null, contacts = emptyList(), isActive = true)

    private fun ministryDto() =
        MinistryDto(
            publicId = "p",
            title = "t",
            subtitle = "s",
            about = "a",
            requirements = emptyList(),
            schedules = emptyList(),
            contacts = emptyList(),
            imageUrl = null,
            isActive = true,
        )

    private fun trainingCatalogDto() =
        TrainingCatalogDto(
            publicId = "p",
            code = "QT_BASIC_SEMINAR",
            name = "n",
            nameKo = null,
            category = null,
            sortOrder = 10,
            hasCohorts = false,
            isActive = true,
            prerequisiteCode = null,
        )

    private fun announcementDto() =
        AnnouncementDto(
            id = "p",
            title = "제목",
            body = "내용",
            category = "NOTICE",
            startAt = "2026-08-30T10:00:00+02:00",
            endAt = null,
            imageUrl = null,
            location = null,
            viewCount = 0,
            isPinned = true,
        )

    private fun reportEntry() = ReportEntry(memberId = "p", isPresent = true, prayerRequest = null)

    private fun memberDto() = MemberDto(publicId = "p", lastName = "김", firstName = "철수", memberStatus = "ACTIVE")

    private fun memberSummaryDto() = MemberSummaryDto(publicId = "p", lastName = "김", firstName = "철수", memberStatus = "ACTIVE")

    private fun eventRsvpDto() =
        EventRsvpDto(
            publicId = "p",
            title = "t",
            windowStart = OffsetDateTime.parse("2026-08-30T10:00:00+02:00"),
            windowEnd = OffsetDateTime.parse("2026-08-30T12:00:00+02:00"),
            isActive = true,
            announcementPublicId = null,
        )
}
