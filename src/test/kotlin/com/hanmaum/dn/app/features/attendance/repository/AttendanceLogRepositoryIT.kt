package com.hanmaum.dn.app.features.attendance.repository

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class)
@Tag("integration")
class AttendanceLogRepositoryIT {
    @Autowired lateinit var repository: AttendanceLogRepository

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `atomic check-in preserves group snapshot and group count query includes zero groups`() {
        val originalGroup = ChurchGroup(division = "청년부", name = "원래 그룹")
        val newGroup = ChurchGroup(division = "청년부", name = "새 그룹")
        entityManager.persist(originalGroup)
        entityManager.persist(newGroup)

        val member = Member(lastName = "김", firstName = "철수", group = originalGroup)
        entityManager.persist(member)

        val definition =
            AttendanceDefinition(
                title = "주일예배",
                dayOfWeek = DayOfWeek.SUNDAY,
                windowStart = LocalTime.of(10, 0),
                windowEnd = LocalTime.of(12, 0),
            )
        entityManager.persist(definition)
        entityManager.flush()

        val attendanceDate = LocalDate.of(2026, 6, 14)
        val firstInsert =
            repository.insertIfAbsent(
                publicId = UUID.randomUUID(),
                definitionId = definition.id!!,
                memberId = member.id!!,
                groupId = originalGroup.id,
                attendanceDate = attendanceDate,
            )

        member.group = newGroup
        entityManager.flush()

        val duplicateInsert =
            repository.insertIfAbsent(
                publicId = UUID.randomUUID(),
                definitionId = definition.id!!,
                memberId = member.id!!,
                groupId = newGroup.id,
                attendanceDate = attendanceDate,
            )
        val counts = repository.countByChurchGroup(definition.id!!, attendanceDate)

        assertEquals(1, firstInsert)
        assertEquals(0, duplicateInsert)
        assertEquals(1, counts.single { it.groupPublicId == originalGroup.publicId }.attendanceCount)
        assertEquals(0, counts.single { it.groupPublicId == newGroup.publicId }.attendanceCount)

        entityManager.remove(member)
        entityManager.flush()

        val anonymizedMemberId =
            jdbcTemplate.queryForObject(
                "SELECT member_id FROM attendance_logs WHERE definition_id = ? AND attendance_date = ?",
                Long::class.javaObjectType,
                definition.id,
                attendanceDate,
            )
        val countsAfterMemberDeletion = repository.countByChurchGroup(definition.id!!, attendanceDate)

        assertEquals(null, anonymizedMemberId)
        assertEquals(1, countsAfterMemberDeletion.single { it.groupPublicId == originalGroup.publicId }.attendanceCount)
    }
}
