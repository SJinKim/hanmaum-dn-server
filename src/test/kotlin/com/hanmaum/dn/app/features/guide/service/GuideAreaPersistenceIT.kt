package com.hanmaum.dn.app.features.guide.service

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.floorplan.domain.Room
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaCreateRequest
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaDefinition
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaOrderItem
import com.hanmaum.dn.app.features.guide.api.v1.dto.GuideAreaOrderRequest
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

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

    @Test fun `detail read query count stays constant when adding another area and room`() {
        val sessionFactory = em.entityManagerFactory.unwrap(org.hibernate.SessionFactory::class.java)
        val statistics = sessionFactory.statistics
        statistics.isStatisticsEnabled = true
        try {
            val initial = reports.create(GuideDetailsCreateRequest(date, entries = guideYouthExample()), "editor")
            em.clear()
            statistics.clear()
            reports.get(initial.report.publicId)
            val before = statistics.prepareStatementCount
            val room = configuration.rooms().first { it.name.contains("카페테리아") }
            configuration.create(GuideAreaCreateRequest(room.publicId, "Additional", 4, true))
            val larger = reports.create(GuideDetailsCreateRequest(date.plusWeeks(1), entries = emptyList()), "editor")
            em.clear()
            statistics.clear()
            val loaded = reports.get(larger.report.publicId)
            assertEquals(5, loaded.rooms.size)
            assertEquals(before, statistics.prepareStatementCount)
        } finally {
            statistics.isStatisticsEnabled = false
        }
    }

    @Test fun `seed skips a missing room without guessing or blocking migration`() {
        em.createNativeQuery("delete from guide_counting_areas").executeUpdate()
        em.createNativeQuery("update room set name = 'renamed sanctuary' where name = '1-1 대예배실'").executeUpdate()
        val sql =
            java.nio.file.Files.readString(
                java.nio.file.Path
                    .of("src/main/resources/db/migration/V20261020160100__seed_guide_counting_areas.sql"),
            )
        em.unwrap(org.hibernate.Session::class.java).doWork { connection -> connection.createStatement().use { it.execute(sql) } }
        em.clear()
        assertEquals(listOf("본당2층C", "유아유치부"), configuration.list().map { it.label })
    }

    @Test fun `append repairs legacy duplicates and reorder conflicts never partially save`() {
        areas.findConfigured().forEach { it.displayOrder = 0 }
        em.flush()
        val added = configuration.create(GuideAreaCreateRequest(configuration.rooms().first().publicId, "Appended", 0, true))
        assertEquals(4, added.displayOrder)
        val initial = configuration.list()
        assertEquals((0..4).toList(), initial.map { it.displayOrder })
        assertEquals(added.areaPublicId, initial.last().areaPublicId)
        val order = GuideAreaOrderRequest(initial.reversed().map { GuideAreaOrderItem(it.areaPublicId, it.version) })
        val changed = configuration.reorder(order)
        assertEquals(initial.reversed().map { it.areaPublicId }, changed.map { it.areaPublicId })
        assertEquals((0..4).toList(), changed.map { it.displayOrder })
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<GuideException> { configuration.reorder(order) }.status)
        assertEquals(changed, configuration.list())
        assertFailsWith<GuideDetailValidationException> {
            configuration.reorder(GuideAreaOrderRequest(listOf(order.areas.first(), order.areas.first())))
        }
        assertEquals(changed, configuration.list())
    }

    @Test fun `membership changes invalidate order and removal preserves historic snapshots and rooms`() {
        val report = reports.create(GuideDetailsCreateRequest(date, entries = guideYouthExample()), "editor")
        val before = configuration.list()
        val stale = GuideAreaOrderRequest(before.map { GuideAreaOrderItem(it.areaPublicId, it.version) })
        configuration.create(GuideAreaCreateRequest(before.first().roomPublicId, "New", active = true))
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<GuideException> { configuration.reorder(stale) }.status)
        val original = configuration.list().first()
        assertEquals(
            HttpStatus.CONFLICT,
            assertFailsWith<GuideException> {
                configuration.remove(original.areaPublicId, original.version)
            }.status,
        )
        val disabled =
            configuration.update(
                original.areaPublicId,
                GuideAreaUpdateRequest(original.roomPublicId, original.label, active = false, version = original.version),
            )
        assertEquals(
            HttpStatus.CONFLICT,
            assertFailsWith<GuideException> {
                configuration.remove(disabled.areaPublicId, original.version)
            }.status,
        )
        val remaining = configuration.remove(disabled.areaPublicId, disabled.version)
        em.clear()
        assertEquals((remaining.indices).toList(), remaining.map { it.displayOrder })
        assertFalse(configuration.list().any { it.areaPublicId == original.areaPublicId })
        assertTrue(configuration.rooms().any { it.publicId == original.roomPublicId })
        assertEquals(report, reports.get(report.report.publicId))
        assertTrue(areas.findAll().single { it.publicId == original.areaPublicId }.deletedAt != null)
        assertEquals(
            HttpStatus.NOT_FOUND,
            assertFailsWith<GuideException> {
                configuration.remove(original.areaPublicId, disabled.version)
            }.status,
        )
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `simultaneous appends serialize before reading the next position`() {
        val before = configuration.list()
        try {
            val second =
                blockedUntilCommit(
                    first = {
                        configuration.create(
                            GuideAreaCreateRequest(before.first().roomPublicId, "Concurrent first", active = true),
                        )
                    },
                    second = {
                        configuration.create(
                            GuideAreaCreateRequest(before.first().roomPublicId, "Concurrent second", active = true),
                        )
                    },
                )
            assertEquals(before.size + 1, second.displayOrder)
            assertEquals((0 until before.size + 2).toList(), configuration.list().map { it.displayOrder })
        } finally {
            TransactionTemplate(transactionManager).executeWithoutResult {
                areas.findConfigured().filter { it.publicId !in before.map { row -> row.areaPublicId } }.forEach(areas::delete)
                areas.flush()
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `concurrent reorder waits then rejects the stale complete snapshot`() {
        val before = configuration.list()
        val order = GuideAreaOrderRequest(before.reversed().map { GuideAreaOrderItem(it.areaPublicId, it.version) })
        try {
            val failure =
                blockedUntilCommit(
                    first = { configuration.reorder(order) },
                    second = {
                        try {
                            configuration.reorder(order)
                            null
                        } catch (exception: GuideException) {
                            exception
                        }
                    },
                )
            assertEquals(HttpStatus.CONFLICT, failure?.status)
            assertEquals(before.reversed().map { it.areaPublicId }, configuration.list().map { it.areaPublicId })
        } finally {
            val versions = configuration.list().associate { it.areaPublicId to it.version }
            configuration.reorder(
                GuideAreaOrderRequest(before.map { GuideAreaOrderItem(it.areaPublicId, versions.getValue(it.areaPublicId)) }),
            )
        }
    }

    private fun <T> blockedUntilCommit(
        first: () -> Unit,
        second: () -> T,
    ): T {
        val written = CountDownLatch(1)
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val writer =
                pool.submit {
                    TransactionTemplate(transactionManager).executeWithoutResult {
                        first()
                        written.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                    }
                }
            assertTrue(written.await(10, TimeUnit.SECONDS))
            val waiting =
                pool.submit<T> {
                    started.countDown()
                    second()
                }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertFailsWith<TimeoutException> { waiting.get(200, TimeUnit.MILLISECONDS) }
            release.countDown()
            writer.get(10, TimeUnit.SECONDS)
            return waiting.get(10, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
}
