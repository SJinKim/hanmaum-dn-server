package com.hanmaum.dn.app.common.cleanup

import com.hanmaum.dn.app.common.observability.OperationOutcome
import com.hanmaum.dn.app.common.observability.OperationalMetrics
import com.hanmaum.dn.app.features.announcements.repository.AnnouncementRepository
import com.hanmaum.dn.app.features.attendance.repository.AttendanceLogRepository
import com.hanmaum.dn.app.features.members.service.MemberPurgeService
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import java.time.Instant
import kotlin.test.assertFailsWith

@ExtendWith(MockitoExtension::class)
class CleanupServiceTest {
    @Mock private lateinit var ministryAssignmentRepository: MinistryAssignmentRepository

    @Mock private lateinit var announcementRepository: AnnouncementRepository

    @Mock private lateinit var attendanceLogRepository: AttendanceLogRepository

    @Mock private lateinit var memberPurgeService: MemberPurgeService

    @Mock private lateinit var operationalMetrics: OperationalMetrics

    @InjectMocks
    private lateinit var cleanupService: CleanupService

    @Test
    fun `purge passes a cutoff timestamp close to now`() {
        val before = Instant.now()
        `when`(ministryAssignmentRepository.hardDeleteExpired(any())).thenReturn(0)
        `when`(announcementRepository.hardDeleteExpired(any())).thenReturn(0)
        `when`(attendanceLogRepository.hardDeleteExpired(any())).thenReturn(0)
        `when`(memberPurgeService.purgeExpired(any())).thenReturn(0)

        cleanupService.purgeExpiredSoftDeletedEntries()

        verify(operationalMetrics).recordBackgroundJob(eq("cleanup"), eq(OperationOutcome.SUCCESS), any())

        val after = Instant.now()
        val captor = argumentCaptor<Instant>()
        verify(ministryAssignmentRepository).hardDeleteExpired(captor.capture())
        val captured = captor.firstValue
        assertTrue(!captured.isBefore(before)) { "cutoff should be >= before" }
        assertTrue(!captured.isAfter(after)) { "cutoff should be <= after" }
    }

    @Test
    fun `purge calls all repositories and member purge`() {
        `when`(ministryAssignmentRepository.hardDeleteExpired(any())).thenReturn(2)
        `when`(announcementRepository.hardDeleteExpired(any())).thenReturn(1)
        `when`(attendanceLogRepository.hardDeleteExpired(any())).thenReturn(0)
        `when`(memberPurgeService.purgeExpired(any())).thenReturn(0)

        cleanupService.purgeExpiredSoftDeletedEntries()

        verify(ministryAssignmentRepository).hardDeleteExpired(any())
        verify(announcementRepository).hardDeleteExpired(any())
        verify(attendanceLogRepository).hardDeleteExpired(any())
        verify(memberPurgeService).purgeExpired(any())
    }

    @Test
    fun `purge continues with remaining tables when one fails`() {
        doThrow(RuntimeException("DB error")).`when`(ministryAssignmentRepository).hardDeleteExpired(any())
        `when`(announcementRepository.hardDeleteExpired(any())).thenReturn(0)
        `when`(attendanceLogRepository.hardDeleteExpired(any())).thenReturn(0)
        `when`(memberPurgeService.purgeExpired(any())).thenReturn(0)

        assertFailsWith<IllegalStateException> {
            cleanupService.purgeExpiredSoftDeletedEntries()
        }

        verify(operationalMetrics).recordBackgroundJob(eq("cleanup"), eq(OperationOutcome.FAILURE), any())

        verify(announcementRepository).hardDeleteExpired(any())
        verify(attendanceLogRepository).hardDeleteExpired(any())
        verify(memberPurgeService).purgeExpired(any())
    }
}
