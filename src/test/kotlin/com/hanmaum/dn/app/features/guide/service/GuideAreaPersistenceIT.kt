package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.floorplan.domain.Room
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaCreateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaDefinition
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaUpdateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsCreateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideDetailsUpdateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideSummaryConfirmation
import com.hanmaum.dn.app.features.guide.api.v1.dto.WorshipCountRequest
import com.hanmaum.dn.app.features.guide.guideAreas
import com.hanmaum.dn.app.features.guide.guideYouthExample
import com.hanmaum.dn.app.features.guide.guideZeroRows
import com.hanmaum.dn.app.features.guide.repository.GuideCountingAreaRepository
import com.hanmaum.dn.app.features.guide.repository.GuideReportAreaRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataJpaTest(properties = ["spring.jpa.show-sql=false"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
@Import(
    PiiCryptoConfiguration::class,
    GuideAreaService::class,
    GuideDetailsService::class,
    GuideDetailsCalculator::class,
    WorshipCountService::class,
)
class GuideAreaPersistenceIT {
    @Autowired private lateinit var configuration: GuideAreaService

    @Autowired private lateinit var reports: GuideDetailsService

    @Autowired private lateinit var summaries: WorshipCountService

    @Autowired private lateinit var areas: GuideCountingAreaRepository

    @Autowired private lateinit var snapshots: GuideReportAreaRepository

    @Autowired private lateinit var em: EntityManager
    private val date = LocalDate.of(2026, 10, 25)

    @Test fun `initial areas reference existing rooms with A and B sharing one physical sanctuary`() {
        val initial = configuration.list()
        assertEquals(guideAreas.map { it.areaPublicId }, initial.map { it.areaPublicId })
        assertEquals(initial[0].roomPublicId, initial[1].roomPublicId)
        assertEquals(3, initial.map { it.roomPublicId }.distinct().size)
        assertTrue(initial.all { it.active })
        assertTrue(configuration.rooms().any { it.name.contains("비전홀") })
        assertFalse(initial.any { it.roomName.contains("비전홀") || it.roomName.contains("소예배실") })
    }

    @Test fun `fifth area is normalized and summed dynamically while previous report membership remains unchanged`() {
        val previous = reports.create(GuideDetailsCreateRequest(date, entries = guideYouthExample()), "editor")
        val room = configuration.rooms().first { it.name.contains("카페테리아") }
        val fifth = configuration.create(GuideAreaCreateRequest(room.publicId, "추가 공간", 4, true))
        val draft = reports.create(GuideDetailsCreateRequest(date.plusWeeks(1), entries = guideYouthExample()), "editor")
        assertEquals(15, draft.entries.size)
        assertEquals(5, draft.rooms.size)
        assertNull(draft.report.totalCount)
        val definitions = draft.rooms.map { GuideAreaDefinition(it.areaPublicId, it.roomPublicId, it.roomName, it.label, it.displayOrder) }
        val complete =
            guideZeroRows(definitions).map {
                if (it.areaPublicId == fifth.areaPublicId &&
                    it.serviceNumber == 3
                ) {
                    it.copy(adultCount = 40, youthCount = 25, schoolCount = 5)
                } else {
                    it
                }
            }
        val saved =
            reports.update(
                draft.report.publicId,
                GuideDetailsUpdateRequest(draft.report.serviceDate, null, draft.report.version, complete),
                "editor",
            )
        em.clear()
        assertEquals(70L, saved.report.totalCount)
        assertEquals(65L, saved.totals?.compatibleAdultCount)
        assertEquals(previous, reports.get(previous.report.publicId))
        assertEquals(4, snapshots.findAllByReportPublicId(previous.report.publicId).size)
        assertFailsWith<GuideDetailValidationException> {
            reports.update(previous.report.publicId, GuideDetailsUpdateRequest(date, null, previous.report.version, complete), "editor")
        }
    }

    @Test fun `configuration rename reorder reassignment deactivation and physical room rename preserve old snapshot and edits`() {
        val original = reports.create(GuideDetailsCreateRequest(date, entries = guideYouthExample()), "editor")
        val area = configuration.list().first()
        val otherRoom = configuration.rooms().first { it.name.contains("카페테리아") }
        val request = GuideAreaUpdateRequest(otherRoom.publicId, "new label", 9, false, area.version)
        val changed = configuration.update(area.areaPublicId, request)
        assertEquals(area.version + 1, changed.version)
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<GuideException> { configuration.update(area.areaPublicId, request) }.status)
        val oldRoom =
            em
                .createQuery("select r from Room r where r.publicId = :id", Room::class.java)
                .setParameter("id", area.roomPublicId)
                .singleResult
        oldRoom.name = "renamed physical room"
        em.flush()
        em.clear()
        assertEquals(original, reports.get(original.report.publicId))
        val edited =
            reports.update(
                original.report.publicId,
                GuideDetailsUpdateRequest(date, "note", original.report.version, guideYouthExample()),
                "editor",
            )
        assertEquals(original.rooms, edited.rooms)
        val future = reports.create(GuideDetailsCreateRequest(date.plusWeeks(1)), "editor")
        assertEquals(9, future.entries.size)
        assertFalse(future.rooms.any { it.areaPublicId == area.areaPublicId })
        assertEquals("renamed physical room", future.rooms.first().roomName)
    }

    @Test fun `deleted rooms and floors are not selectable or snapshotted but historical data stays readable and editable`() {
        val original = reports.create(GuideDetailsCreateRequest(date, entries = guideYouthExample()), "editor")
        val first = areas.findConfigured().first()
        val roomId = first.room.publicId
        first.room.deletedAt = Instant.now()
        em.flush()
        assertFalse(configuration.rooms().any { it.publicId == roomId })
        assertFailsWith<GuideDetailValidationException> { configuration.create(GuideAreaCreateRequest(roomId, "invalid", 0, true)) }
        val response = configuration.list().first { it.areaPublicId == first.publicId }
        configuration.update(first.publicId, GuideAreaUpdateRequest(roomId, response.label, response.displayOrder, false, response.version))
        em.flush()
        em.clear()
        assertEquals(original, reports.get(original.report.publicId))
        val edited =
            reports.update(
                original.report.publicId,
                GuideDetailsUpdateRequest(date, null, original.report.version, guideYouthExample()),
                "editor",
            )
        assertEquals(70L, edited.report.totalCount)
        val next = reports.create(GuideDetailsCreateRequest(date.plusWeeks(1)), "editor")
        assertEquals(2, next.rooms.size)
        assertFalse(next.rooms.any { it.roomPublicId == roomId })
        val remaining = areas.findConfigured().first { it.room.deletedAt == null }
        val floorId = remaining.room.floor.publicId
        remaining.room.floor.deletedAt = Instant.now()
        em.flush()
        assertFalse(configuration.rooms().any { it.floorPublicId == floorId })
    }

    @Test fun `empty configuration rejects new details and summary conversion without mutating the original report`() {
        areas.findConfigured().forEach { it.active = false }
        em.flush()
        val summary = summaries.create(WorshipCountRequest(date, 1, 2, 3), "editor")
        assertEquals(
            HttpStatus.CONFLICT,
            assertFailsWith<GuideException> {
                reports.create(GuideDetailsCreateRequest(date.plusWeeks(1)), "editor")
            }.status,
        )
        assertEquals(
            HttpStatus.CONFLICT,
            assertFailsWith<GuideException> {
                reports.update(
                    summary.publicId,
                    GuideDetailsUpdateRequest(date, null, summary.version, emptyList(), GuideSummaryConfirmation(1, 2, 3)),
                    "editor",
                )
            }.status,
        )
        assertEquals(summary, summaries.get(summary.publicId))
        assertTrue(snapshots.findAllByReportPublicId(summary.publicId).isEmpty())
        assertFailsWith<GuideDetailValidationException> {
            configuration.create(GuideAreaCreateRequest(UUID.randomUUID(), "missing room", 0, true))
        }
        assertFailsWith<GuideDetailValidationException> {
            configuration.create(GuideAreaCreateRequest(configuration.rooms().first().publicId, " ", -1, true))
        }
    }
}
