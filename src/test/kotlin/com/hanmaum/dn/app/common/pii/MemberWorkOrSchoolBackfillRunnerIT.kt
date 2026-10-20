package com.hanmaum.dn.app.common.pii

import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class, MemberWorkOrSchoolBackfillRunner::class)
@Tag("integration")
class MemberWorkOrSchoolBackfillRunnerIT {
    @Autowired lateinit var runner: MemberWorkOrSchoolBackfillRunner

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var entityManager: EntityManager

    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `moves the legacy value to the member, clears the source and is idempotent`() {
        val (memberId, profileId) = seed(memberValue = null, legacyValue = "  TU Berlin  ")

        runner.run(DefaultApplicationArguments())
        runner.run(DefaultApplicationArguments())
        entityManager.clear()

        assertEquals("TU Berlin", memberRepository.findById(memberId).get().workOrSchool)
        assertNull(legacyColumn(profileId))
    }

    @Test
    fun `keeps a value the member already has`() {
        val (memberId, profileId) = seed(memberValue = "Siemens", legacyValue = "TU Berlin")

        runner.run(DefaultApplicationArguments())
        entityManager.clear()

        assertEquals("Siemens", memberRepository.findById(memberId).get().workOrSchool)
        assertNull(legacyColumn(profileId))
    }

    private fun seed(
        memberValue: String?,
        legacyValue: String,
    ): Pair<Long, Long> {
        val member = Member(lastName = "김", firstName = "새봄", workOrSchool = memberValue)
        entityManager.persist(member)
        val profile = NewcomerProfile(member = member)
        entityManager.persist(profile)
        entityManager.flush()
        jdbcTemplate.update(
            "UPDATE newcomer_profiles SET work_or_school = ? WHERE id = ?",
            PiiCryptoContext.encrypt(legacyValue, "newcomer_profiles.work_or_school"),
            profile.id,
        )
        entityManager.clear()
        return member.id!! to profile.id!!
    }

    private fun legacyColumn(profileId: Long): String? =
        jdbcTemplate.queryForObject(
            "SELECT work_or_school FROM newcomer_profiles WHERE id = ?",
            String::class.java,
            profileId,
        )
}
