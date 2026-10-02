package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.UsersResource
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class MemberPurgeServiceTest {
    @Mock private lateinit var memberRepository: MemberRepository

    @Mock private lateinit var jdbcTemplate: JdbcTemplate

    @Mock private lateinit var transactionTemplate: TransactionTemplate

    @Mock private lateinit var transactionStatus: TransactionStatus

    @Mock private lateinit var keycloak: Keycloak

    @Test
    fun `purgeExpired deletes dependent rows and member in one transaction`() {
        val now = Instant.parse("2026-06-15T00:00:00Z")
        val member =
            Member(lastName = "Kim", firstName = "Min").apply {
                id = 42L
                deletedAt = now.minusSeconds(60)
                deleteEntryAt = now
            }
        `when`(memberRepository.findAllByDeleteEntryAtLessThanEqualAndDeletedAtIsNotNull(now))
            .thenReturn(listOf(member))
        doAnswer { invocation ->
            invocation.getArgument<Consumer<TransactionStatus>>(0).accept(transactionStatus)
            null
        }.`when`(transactionTemplate).executeWithoutResult(any())

        val service =
            MemberPurgeService(
                memberRepository = memberRepository,
                jdbcTemplate = jdbcTemplate,
                transactionTemplate = transactionTemplate,
                keycloak = keycloak,
                realm = "test-realm",
            )

        assertEquals(1, service.purgeExpired(now))

        verify(jdbcTemplate).update(
            eq("DELETE FROM car_passengers WHERE car_id IN (SELECT id FROM cars WHERE driver_member_id = ?)"),
            eq(42L),
        )
        verify(jdbcTemplate).update(eq("DELETE FROM car_passengers WHERE member_id = ?"), eq(42L))
        verify(jdbcTemplate).update(eq("DELETE FROM cars WHERE driver_member_id = ?"), eq(42L))
        verify(jdbcTemplate).update(eq("DELETE FROM meeting_attendances WHERE member_id = ?"), eq(42L))
        verify(jdbcTemplate, never()).update(eq("DELETE FROM attendance_logs WHERE member_id = ?"), eq(42L))
        verify(jdbcTemplate).update(eq("DELETE FROM ministry_registrations WHERE member_id = ?"), eq(42L))
        // group_leaders.member_id is an FK without ON DELETE CASCADE — skipping this delete
        // makes the member row deletion below fail with a constraint violation.
        verify(jdbcTemplate).update(eq("DELETE FROM group_leaders WHERE member_id = ?"), eq(42L))
        verify(jdbcTemplate).update(eq("DELETE FROM user_training WHERE user_id = ?"), eq(42L))
        verify(jdbcTemplate).update(
            eq("DELETE FROM family_relationships WHERE member_id = ? OR related_member_id = ?"),
            eq(42L),
            eq(42L),
        )
        verify(memberRepository).deleteById(42L)
        verify(memberRepository).flush()
    }

    private fun service() =
        MemberPurgeService(
            memberRepository = memberRepository,
            jdbcTemplate = jdbcTemplate,
            transactionTemplate = transactionTemplate,
            keycloak = keycloak,
            realm = "test-realm",
        )

    @Test
    fun `purgeMember deletes the Keycloak user and the member rows`() {
        val publicId = UUID.randomUUID()
        val member =
            Member(lastName = "Kim", firstName = "Min").apply {
                id = 42L
                keycloakId = "kc-42"
                memberStatus = MemberStatus.DELETED
                deletedAt = Instant.parse("2026-06-01T00:00:00Z")
            }
        `when`(memberRepository.findByPublicId(publicId)).thenReturn(Optional.of(member))
        val realmResource = mock<RealmResource>()
        val usersResource = mock<UsersResource>()
        val response = mock<Response>()
        `when`(keycloak.realm("test-realm")).thenReturn(realmResource)
        `when`(realmResource.users()).thenReturn(usersResource)
        `when`(usersResource.delete("kc-42")).thenReturn(response)
        `when`(response.status).thenReturn(204)
        doAnswer { invocation ->
            invocation.getArgument<Consumer<TransactionStatus>>(0).accept(transactionStatus)
            null
        }.`when`(transactionTemplate).executeWithoutResult(any())

        service().purgeMember(publicId)

        verify(usersResource).delete("kc-42")
        verify(jdbcTemplate).update(eq("DELETE FROM group_leaders WHERE member_id = ?"), eq(42L))
        verify(memberRepository).deleteById(42L)
        verify(memberRepository).flush()
    }

    @Test
    fun `purgeMember rejects a member that is not deleted without touching Keycloak`() {
        val publicId = UUID.randomUUID()
        val member =
            Member(lastName = "Kim", firstName = "Min").apply {
                id = 42L
                keycloakId = "kc-42"
                memberStatus = MemberStatus.ACTIVE
            }
        `when`(memberRepository.findByPublicId(publicId)).thenReturn(Optional.of(member))

        val exception = assertThrows<ResponseStatusException> { service().purgeMember(publicId) }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        verifyNoInteractions(keycloak, jdbcTemplate, transactionTemplate)
        verify(memberRepository, never()).deleteById(any())
    }

    @Test
    fun `purgeMember throws when the member does not exist`() {
        val publicId = UUID.randomUUID()
        `when`(memberRepository.findByPublicId(publicId)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { service().purgeMember(publicId) }

        verifyNoInteractions(keycloak, jdbcTemplate, transactionTemplate)
    }
}
