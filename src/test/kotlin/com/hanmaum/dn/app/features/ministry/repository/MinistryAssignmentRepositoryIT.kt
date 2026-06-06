package com.hanmaum.dn.app.features.ministry.repository

import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles

/**
 * Guards against the replace-set delete detaching the member it was loaded with.
 *
 * Regression: `deleteByMemberId` was annotated `@Modifying(clearAutomatically = true)`,
 * which clears the whole persistence context. In `MemberService.replaceMemberMinistries`
 * that detached the already-loaded `member`, so building the response DTO threw
 * `LazyInitializationException` when it read the member's lazy `group`.
 *
 * JPA slice (no Keycloak/web context); needs the test DB (see -PincludeIntegration).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Tag("integration")
class MinistryAssignmentRepositoryIT {
    @Autowired lateinit var members: MemberRepository

    @Autowired lateinit var assignments: MinistryAssignmentRepository

    @PersistenceContext lateinit var em: EntityManager

    @Test
    fun `deleteByMemberId keeps the loaded member's lazy group accessible`() {
        val group = ChurchGroup(name = "다니엘조")
        em.persist(group)
        val member = Member(lastName = "김", firstName = "철수").apply { this.group = group }
        em.persist(member)
        em.flush()
        em.clear()

        // Reload so `group` is an uninitialized lazy proxy, as in a real request.
        val reloaded = members.findByPublicIdAndDeletedAtIsNull(member.publicId).orElseThrow()

        // The replace-set flow deletes the member's assignments first; this must NOT clear
        // the persistence context (doing so detaches `reloaded` and its group proxy).
        assignments.deleteByMemberId(reloaded.id!!)

        // Would throw LazyInitializationException if the context was cleared above.
        assertEquals("다니엘조", reloaded.group?.name)
    }
}
