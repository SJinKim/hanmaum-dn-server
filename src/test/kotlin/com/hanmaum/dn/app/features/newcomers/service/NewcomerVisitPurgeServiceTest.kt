package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.features.newcomers.repository.NewcomerVisitRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@ExtendWith(MockitoExtension::class)
class NewcomerVisitPurgeServiceTest {
    @Mock private lateinit var visitRepo: NewcomerVisitRepository

    // 2026-06-14 00:30 in Berlin is still 2026-06-13 in UTC: the cutoff follows the clock's zone.
    private val now = Instant.parse("2026-06-13T22:30:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Berlin"))

    @Test
    fun `purge uses the configured years and deleted retention`() {
        val service = NewcomerVisitPurgeService(visitRepo, clock, retentionYears = 3, deletedRetentionDays = 30)
        `when`(visitRepo.hardDeleteVisitedBefore(LocalDate.of(2023, 6, 14))).thenReturn(4)
        `when`(visitRepo.hardDeleteSoftDeletedBefore(Instant.parse("2026-05-14T22:30:00Z"))).thenReturn(1)

        assertEquals(5, service.purgeExpired(now))
    }

    @Test
    fun `a shorter retention moves the cutoff`() {
        val service = NewcomerVisitPurgeService(visitRepo, clock, retentionYears = 1, deletedRetentionDays = 30)

        service.purgeExpired(now)

        verify(visitRepo).hardDeleteVisitedBefore(LocalDate.of(2025, 6, 14))
    }
}
