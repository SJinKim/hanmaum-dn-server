package com.hanmaum.dn.app.features.bulletin.service

import com.hanmaum.dn.app.features.bulletin.api.v1.dto.BulletinServiceRequest
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSectionKey
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSectionTitle
import com.hanmaum.dn.app.features.bulletin.domain.BulletinService
import com.hanmaum.dn.app.features.bulletin.repository.BulletinEditionRepository
import com.hanmaum.dn.app.features.bulletin.repository.BulletinSectionTitleRepository
import com.hanmaum.dn.app.features.bulletin.repository.BulletinServiceRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class BulletinSettingsServiceTest {
    @Mock private lateinit var services: BulletinServiceRepository

    @Mock private lateinit var editions: BulletinEditionRepository

    @Mock private lateinit var sectionTitles: BulletinSectionTitleRepository

    private val now: Instant = Instant.parse("2026-10-06T10:00:00Z")

    private val settings by lazy { BulletinSettingsService(services, editions, sectionTitles, Clock.fixed(now, ZoneOffset.UTC)) }

    private fun service(
        name: String,
        isDefault: Boolean = false,
    ) = BulletinService(name, LocalTime.of(11, 0), 0, isBulletinDefault = isDefault)

    private fun request(
        isDefault: Boolean = false,
        active: Boolean = true,
    ) = BulletinServiceRequest(name = " 2부 예배 ", startTime = LocalTime.of(13, 30), active = active, isBulletinDefault = isDefault)

    private fun stubSave() {
        `when`(services.saveAndFlush(any<BulletinService>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `marking a service as default takes the mark from the previous one`() {
        val old = service("1부 예배", isDefault = true)
        val target = service("2부 예배")
        `when`(services.findByPublicIdAndDeletedAtIsNull(target.publicId)).thenReturn(target)
        `when`(services.findByIsBulletinDefaultTrueAndDeletedAtIsNull()).thenReturn(old)
        stubSave()

        val response = settings.updateService(target.publicId, request(isDefault = true), "kc-001")

        assertFalse(old.isBulletinDefault)
        assertTrue(target.isBulletinDefault)
        assertTrue(response.isBulletinDefault)
        assertEquals("2부 예배", target.name)
    }

    @Test
    fun `the default cannot be unmarked or deactivated, only moved`() {
        val default = service("1부 예배", isDefault = true)
        `when`(services.findByPublicIdAndDeletedAtIsNull(default.publicId)).thenReturn(default)

        for (req in listOf(request(isDefault = false), request(isDefault = true, active = false))) {
            val e = assertThrows<ResponseStatusException> { settings.updateService(default.publicId, req, "kc-001") }
            assertEquals(HttpStatus.CONFLICT, e.statusCode)
        }
        assertTrue(default.isBulletinDefault)
        assertTrue(default.active)
    }

    @Test
    fun `the default cannot be deleted`() {
        val default = service("1부 예배", isDefault = true)
        `when`(services.findByPublicIdAndDeletedAtIsNull(default.publicId)).thenReturn(default)

        val e = assertThrows<ResponseStatusException> { settings.deleteService(default.publicId, "kc-001") }
        assertEquals(HttpStatus.CONFLICT, e.statusCode)
        assertNull(default.deletedAt)
    }

    @Test
    fun `a service an edition points at is only deactivated`() {
        val used = service("3부 예배")
        `when`(services.findByPublicIdAndDeletedAtIsNull(used.publicId)).thenReturn(used)
        `when`(editions.existsByService(used)).thenReturn(true)
        stubSave()

        val response = settings.deleteService(used.publicId, "kc-001")

        assertNotNull(response)
        assertFalse(used.active)
        assertNull(used.deletedAt)
    }

    @Test
    fun `an unused service is soft-deleted`() {
        val unused = service("3부 예배")
        `when`(services.findByPublicIdAndDeletedAtIsNull(unused.publicId)).thenReturn(unused)
        `when`(editions.existsByService(unused)).thenReturn(false)
        stubSave()

        assertNull(settings.deleteService(unused.publicId, "kc-001"))
        assertEquals(now, unused.deletedAt)
    }

    @Test
    fun `a blank section title goes back to the default title`() {
        val section = BulletinSectionTitle(BulletinSectionKey.SECTION_WORSHIP, "찬양과 경배", "예배의 부름")
        `when`(sectionTitles.findById(BulletinSectionKey.SECTION_WORSHIP)).thenReturn(Optional.of(section))
        `when`(sectionTitles.saveAndFlush(any<BulletinSectionTitle>())).thenAnswer { it.arguments[0] }

        val response = settings.updateSectionTitle(BulletinSectionKey.SECTION_WORSHIP, "  ", "kc-001")

        assertEquals("예배의 부름", section.title)
        assertEquals("예배의 부름", response.title)
    }

    @Test
    fun `a new non-default service leaves the current default alone`() {
        stubSave()

        settings.createService(request(), "kc-001")

        verify(services, never()).findByIsBulletinDefaultTrueAndDeletedAtIsNull()
    }

    @Test
    fun `a new service cannot start as an inactive default`() {
        val e = assertThrows<ResponseStatusException> { settings.createService(request(isDefault = true, active = false), "kc-001") }

        assertEquals(HttpStatus.CONFLICT, e.statusCode)
        verify(services, never()).findByIsBulletinDefaultTrueAndDeletedAtIsNull()
        verify(services, never()).saveAndFlush(any<BulletinService>())
    }

    @Test
    fun `an inactive service cannot be promoted to default`() {
        val target = service("2부 예배")
        `when`(services.findByPublicIdAndDeletedAtIsNull(target.publicId)).thenReturn(target)

        val e =
            assertThrows<ResponseStatusException> {
                settings.updateService(target.publicId, request(isDefault = true, active = false), "kc-001")
            }

        assertEquals(HttpStatus.CONFLICT, e.statusCode)
        assertFalse(target.isBulletinDefault)
        verify(services, never()).findByIsBulletinDefaultTrueAndDeletedAtIsNull()
        verify(services, never()).saveAndFlush(any<BulletinService>())
    }
}
