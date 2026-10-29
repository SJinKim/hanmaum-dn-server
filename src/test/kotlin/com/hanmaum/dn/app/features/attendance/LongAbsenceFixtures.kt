package com.hanmaum.dn.app.features.attendance

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.attendance.domain.AttendanceDefinition
import com.hanmaum.dn.app.features.attendance.domain.AttendanceLog
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import jakarta.persistence.EntityManager
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

/** The 주일 2부 definition configured in the long-absence ITs. */
val NAMED_SERVICE_ID: UUID = UUID.fromString("5f7c2f0e-375a-4c3e-9d5b-000000000375")

const val NAMED_SERVICE_PROPERTY = "app.long-absence.sunday-service-definition-ids=5f7c2f0e-375a-4c3e-9d5b-000000000375"

/** A clock the test moves; the services read it on every call. */
class MutableClock(
    var instant: Instant,
) : Clock() {
    override fun getZone(): ZoneId = BERLIN

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = instant

    fun setToday(date: LocalDate) {
        instant = date.atTime(10, 0).atZone(BERLIN).toInstant()
    }
}

fun berlin(
    date: String,
    time: String,
): Instant = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)).atZone(BERLIN).toInstant()

/** Persists 장기결석자 fixtures with real SQL timestamps; created_at is pinned after insert. */
class LongAbsenceFixtures(
    private val entityManager: EntityManager,
    private val jdbcTemplate: JdbcTemplate,
) {
    private var counter = 0

    val group: ChurchGroup by lazy { ChurchGroup(division = "청년부", name = "1순-${UUID.randomUUID()}").also(entityManager::persist) }

    val named: AttendanceDefinition by lazy {
        definition("주일 2부 예배").also {
            jdbcTemplate.update("UPDATE attendance_definitions SET public_id = ? WHERE id = ?", NAMED_SERVICE_ID, it.id)
        }
    }

    val other: AttendanceDefinition by lazy { definition("수요예배") }

    fun definition(title: String): AttendanceDefinition =
        AttendanceDefinition(
            title = title,
            dayOfWeek = DayOfWeek.SUNDAY,
            windowStart = LocalTime.of(10, 0),
            windowEnd = LocalTime.of(12, 0),
        ).also {
            entityManager.persist(it)
            entityManager.flush()
        }

    fun member(
        status: MemberStatus = MemberStatus.ACTIVE,
        linked: Boolean = true,
        group: ChurchGroup? = this.group,
        firstName: String = "길동${++counter}",
    ): Member =
        Member(
            lastName = "홍",
            firstName = firstName,
            memberStatus = status,
            group = group,
            keycloakId = if (linked) "kc-$firstName-${UUID.randomUUID()}" else null,
        ).also {
            entityManager.persist(it)
            entityManager.flush()
        }

    fun log(
        member: Member,
        date: String,
        createdAt: Instant = berlin(date, "10:00"),
        definition: AttendanceDefinition = named,
        attended: Boolean = true,
    ): AttendanceLog {
        val log = AttendanceLog(definition = definition, member = member, attendanceDate = LocalDate.parse(date), attended = attended)
        entityManager.persist(log)
        entityManager.flush()
        jdbcTemplate.update("UPDATE attendance_logs SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), log.id)
        return log
    }
}
