package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerImportRecordRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class NewcomerImportServiceTest {
    private val members = mock<MemberRepository>()
    private val profiles = mock<NewcomerProfileRepository>()
    private val records = mock<NewcomerImportRecordRepository>()
    private val service = NewcomerImportService(NewcomerImportReader(), members, profiles, records)

    @Test
    fun dryRunReportsAValidRowWithoutWritingMemberOrProvenanceData() {
        val file = Files.createTempFile("newcomer-import-", ".csv")
        file.writeText(
            """
            성,이름,성별
            가,나,남
            """.trimIndent(),
        )

        val report =
            service.import(
                file,
                NewcomerImportLayout.SPREADSHEET_A,
                dryRun = true,
                decisions = emptyMap(),
                caregivers = emptyMap(),
            )

        assertEquals(1, report.read)
        assertEquals(1, report.imported)
        assertEquals(0, report.invalid)
        verify(members, never()).save(org.mockito.kotlin.any())
        verify(profiles, never()).save(org.mockito.kotlin.any())
        verify(records, never()).save(org.mockito.kotlin.any())
    }

    @Test
    fun `reports invalid controlled values instead of importing them as empty`() {
        val file = Files.createTempFile("newcomer-import-", ".csv")
        file.writeText(
            """
            성,이름,성별,세례
            가,나,unrecognized,unknown
            """.trimIndent(),
        )

        val report = service.import(file, NewcomerImportLayout.SPREADSHEET_A, true, emptyMap(), emptyMap())

        assertEquals(0, report.imported)
        assertEquals(setOf("INVALID_GENDER", "INVALID_BAPTISM"), report.issues.map { it.code }.toSet())
        verify(members, never()).save(any())
    }

    @Test
    fun `persists first visit date on the newcomer profile`() {
        val file = Files.createTempFile("newcomer-import-", ".csv")
        file.writeText(
            """
            성,이름,첫방문일
            가,나,2026-09-20
            """.trimIndent(),
        )
        val member = Member("가", "나")
        member.id = 1L
        whenever(members.save(any<Member>())).thenReturn(member)
        whenever(profiles.findByMemberIdAndDeletedAtIsNull(1L)).thenReturn(null)
        whenever(profiles.save(any<NewcomerProfile>())).thenAnswer { invocation -> invocation.arguments[0] }

        service.import(file, NewcomerImportLayout.SPREADSHEET_A, false, emptyMap(), emptyMap())

        verify(profiles).save(
            org.mockito.kotlin.check<NewcomerProfile> { profile ->
                assertEquals(java.time.LocalDate.of(2026, 9, 20), profile.firstVisitDate)
            },
        )
    }
}
