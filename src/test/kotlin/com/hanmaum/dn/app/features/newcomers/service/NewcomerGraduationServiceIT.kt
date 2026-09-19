package com.hanmaum.dn.app.features.newcomers.service

import com.hanmaum.dn.app.common.pii.PiiCryptoConfiguration
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.GraduateNewcomerRequest
import com.hanmaum.dn.app.features.newcomers.api.v1.dto.NewcomerGraduationResponse
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerLifecycle
import com.hanmaum.dn.app.features.newcomers.domain.NewcomerProfile
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerGraduationRepository
import com.hanmaum.dn.app.features.newcomers.repository.NewcomerProfileRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PiiCryptoConfiguration::class, NewcomerGraduationService::class)
@Tag("integration")
class NewcomerGraduationServiceIT {
    @Autowired private lateinit var service: NewcomerGraduationService

    @Autowired private lateinit var memberRepository: MemberRepository

    @Autowired private lateinit var profileRepository: NewcomerProfileRepository

    @Autowired private lateinit var graduationRepository: NewcomerGraduationRepository

    @Autowired private lateinit var groupRepository: ChurchGroupRepository

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @AfterEach
    fun cleanUp() {
        graduationRepository.deleteAll()
        profileRepository.deleteAll()
        memberRepository.deleteAll()
        groupRepository.deleteAll()
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `concurrent identical graduation requests create one audit record and reuse one member`() {
        val member = memberRepository.saveAndFlush(Member(lastName = "김", firstName = "새봄"))
        val profile = profileRepository.saveAndFlush(NewcomerProfile(member))
        val group = groupRepository.saveAndFlush(ChurchGroup(name = "다니엘순"))
        val request = GraduateNewcomerRequest(group.publicId.toString(), 3, LocalDate.of(2027, 1, 5), "정착")
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val responses =
                (1..2).map {
                    executor.submit<NewcomerGraduationResponse> {
                        ready.countDown()
                        start.await(10, TimeUnit.SECONDS)
                        service.graduate(profile.publicId, request, "admin-sub")
                    }
                }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            val completed = responses.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, graduationRepository.count())
            assertEquals(1, completed.map { it.publicId }.toSet().size)
            assertEquals(NewcomerLifecycle.GRADUATED, profileRepository.findById(profile.id!!).get().lifecycleStatus)
            assertEquals(
                group.id,
                jdbcTemplate.queryForObject("SELECT group_id FROM members WHERE id = ?", Long::class.java, member.id),
            )
        } finally {
            executor.shutdownNow()
        }
    }
}
