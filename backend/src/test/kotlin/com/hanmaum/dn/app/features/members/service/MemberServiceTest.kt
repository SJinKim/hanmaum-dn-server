package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.RegisterMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import jakarta.persistence.EntityNotFoundException
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.representations.idm.UserRepresentation
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MemberServiceTest {
    @Mock private lateinit var memberRepository: MemberRepository

    @Mock private lateinit var churchGroupRepository: ChurchGroupRepository

    @Mock private lateinit var keycloak: Keycloak

    @Mock private lateinit var realmResource: RealmResource

    @Mock private lateinit var usersResource: UsersResource

    @Mock private lateinit var kcResponse: Response

    private lateinit var memberService: MemberService

    @BeforeEach
    fun setUp() {
        memberService = MemberService(memberRepository, churchGroupRepository, keycloak, "test-realm")
    }

    private fun memberWithId(
        id: Long,
        firstName: String = "철수",
        lastName: String = "김",
    ): Member {
        val m = Member(lastName = lastName, firstName = firstName)
        m.id = id
        return m
    }

    private fun group(
        id: Long,
        name: String = "다니엘조",
    ): ChurchGroup {
        val g = ChurchGroup(name = name)
        g.id = id
        return g
    }

    private fun setupKeycloakMock(statusCode: Int = 201) {
        `when`(keycloak.realm("test-realm")).thenReturn(realmResource)
        `when`(realmResource.users()).thenReturn(usersResource)
        `when`(usersResource.create(any<UserRepresentation>())).thenReturn(kcResponse)
        `when`(kcResponse.status).thenReturn(statusCode)
        if (statusCode == 201) {
            `when`(kcResponse.location).thenReturn(null)
        }
    }

    private fun registerReq(
        email: String = "test@example.com",
        firstName: String = "철수",
        lastName: String = "김",
    ) = RegisterMemberRequest(
        firstName = firstName,
        lastName = lastName,
        password = "secret123",
        email = email,
        city = "서울",
    )

    // --- getMemberByPublicId ---

    @Test
    fun `getMemberByPublicId throws EntityNotFoundException when member not found`() {
        val id = UUID.randomUUID()
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            memberService.getMemberByPublicId(id)
        }
    }

    @Test
    fun `getMemberByPublicId returns MemberDto when found`() {
        val member = memberWithId(1L)
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(member.publicId))
            .thenReturn(Optional.of(member))

        val result = memberService.getMemberByPublicId(member.publicId)

        assertEquals(member.publicId.toString(), result.publicId)
        assertEquals(member.firstName, result.firstName)
    }

    // --- getMembers ---

    @Test
    fun `getMembers delegates to repository with pageable`() {
        val members = listOf(memberWithId(1L), memberWithId(2L))
        `when`(memberRepository.findActiveMembers(any(), any<Pageable>()))
            .thenReturn(PageImpl(members))

        val result = memberService.getMembers(null, 0, 20)

        assertEquals(2, result.totalElements)
    }

    // --- createMember ---

    @Test
    fun `createMember saves member without group when groupId is null`() {
        val req = CreateMemberRequest(lastName = "김", firstName = "철수")
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }

        val result = memberService.createMember(req)

        assertNull(result.groupName)
        verify(churchGroupRepository, never()).findById(anyLong())
    }

    @Test
    fun `createMember assigns group when groupId provided`() {
        val grp = group(5L, "다니엘조")
        val req = CreateMemberRequest(lastName = "김", firstName = "철수", groupId = 5L)
        `when`(churchGroupRepository.findById(5L)).thenReturn(Optional.of(grp))
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }

        val result = memberService.createMember(req)

        assertEquals("다니엘조", result.groupName)
    }

    @Test
    fun `createMember throws EntityNotFoundException when group not found`() {
        val req = CreateMemberRequest(lastName = "김", firstName = "철수", groupId = 99L)
        `when`(churchGroupRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { memberService.createMember(req) }
    }

    @Test
    fun `createMember throws conflict when email already in use`() {
        val req = CreateMemberRequest(lastName = "김", firstName = "철수", email = "dup@example.com")
        `when`(memberRepository.findByEmailAndDeletedAtIsNull("dup@example.com"))
            .thenReturn(memberWithId(1L))

        assertThrows<ResponseStatusException> { memberService.createMember(req) }
    }

    // --- updateMember ---

    @Test
    fun `updateMember does not call groupRepository when groupId is null`() {
        val member = memberWithId(1L)
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(member.publicId))
            .thenReturn(Optional.of(member))
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }

        memberService.updateMember(
            member.publicId,
            UpdateMemberRequest(lastName = "김", firstName = "철수", groupId = null),
        )

        verify(churchGroupRepository, never()).findById(anyLong())
    }

    @Test
    fun `updateMember updates group when groupId changes`() {
        val oldGroup = group(1L, "구 그룹")
        val newGroup = group(2L, "새 그룹")
        val member = memberWithId(10L)
        member.group = oldGroup
        val req = UpdateMemberRequest(lastName = "김", firstName = "철수", groupId = 2L)

        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(member.publicId))
            .thenReturn(Optional.of(member))
        `when`(churchGroupRepository.findById(2L)).thenReturn(Optional.of(newGroup))
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }

        val result = memberService.updateMember(member.publicId, req)

        assertEquals("새 그룹", result.groupName)
    }

    @Test
    fun `updateMember does not call groupRepository when groupId is same as current`() {
        val grp = group(1L, "기존 그룹")
        val member = memberWithId(10L)
        member.group = grp
        val req = UpdateMemberRequest(lastName = "김", firstName = "철수", groupId = 1L)

        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(member.publicId))
            .thenReturn(Optional.of(member))
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }

        memberService.updateMember(member.publicId, req)

        verify(churchGroupRepository, never()).findById(anyLong())
    }

    // --- softDeleteMember ---

    @Test
    fun `softDeleteMember sets deletedAt and status to DELETED`() {
        val member = memberWithId(1L)
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(member.publicId))
            .thenReturn(Optional.of(member))
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }

        memberService.softDeleteMember(member.publicId)

        assertNotNull(member.deletedAt)
        assertEquals(MemberStatus.DELETED, member.memberStatus)
    }

    // --- registerMember ---

    @Test
    fun `registerMember throws conflict when email already exists`() {
        val existing = memberWithId(1L)
        `when`(memberRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(existing)

        assertThrows<ResponseStatusException> {
            memberService.registerMember(registerReq(email = "test@example.com"))
        }
    }

    @Test
    fun `registerMember assigns null discriminator for first member with that name`() {
        val req = registerReq()
        `when`(memberRepository.findByEmailAndDeletedAtIsNull(req.email)).thenReturn(null)
        `when`(memberRepository.findSimilarNames(req.firstName, req.lastName)).thenReturn(emptyList())
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }
        setupKeycloakMock()

        val result = memberService.registerMember(req)

        assertNull(result.discriminator)
    }

    @Test
    fun `registerMember assigns discriminator A when one member with null discriminator exists`() {
        val req = registerReq(email = "new@example.com")
        val existingMember = Member(lastName = "김", firstName = "철수", discriminator = null)
        `when`(memberRepository.findByEmailAndDeletedAtIsNull(req.email)).thenReturn(null)
        `when`(memberRepository.findSimilarNames(req.firstName, req.lastName)).thenReturn(listOf(existingMember))
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }
        setupKeycloakMock()

        val result = memberService.registerMember(req)

        assertEquals("A", result.discriminator)
    }

    @Test
    fun `registerMember assigns discriminator B when A is already taken`() {
        val req = registerReq(email = "newer@example.com")
        val e1 = Member(lastName = "김", firstName = "철수", discriminator = null)
        val e2 = Member(lastName = "김", firstName = "철수", discriminator = "A")
        `when`(memberRepository.findByEmailAndDeletedAtIsNull(req.email)).thenReturn(null)
        `when`(memberRepository.findSimilarNames(req.firstName, req.lastName)).thenReturn(listOf(e1, e2))
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }
        setupKeycloakMock()

        val result = memberService.registerMember(req)

        assertEquals("B", result.discriminator)
    }

    @Test
    fun `registerMember assigns discriminator C when A and B are taken`() {
        val req = registerReq(email = "newest@example.com")
        val members =
            listOf(
                Member(lastName = "김", firstName = "철수", discriminator = null),
                Member(lastName = "김", firstName = "철수", discriminator = "A"),
                Member(lastName = "김", firstName = "철수", discriminator = "B"),
            )
        `when`(memberRepository.findByEmailAndDeletedAtIsNull(req.email)).thenReturn(null)
        `when`(memberRepository.findSimilarNames(req.firstName, req.lastName)).thenReturn(members)
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }
        setupKeycloakMock()

        val result = memberService.registerMember(req)

        assertEquals("C", result.discriminator)
    }

    @Test
    fun `registerMember throws RuntimeException when Keycloak returns non-201`() {
        val req = registerReq()
        `when`(memberRepository.findByEmailAndDeletedAtIsNull(req.email)).thenReturn(null)
        `when`(memberRepository.findSimilarNames(req.firstName, req.lastName)).thenReturn(emptyList())
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }
        setupKeycloakMock(statusCode = 409)
        `when`(kcResponse.readEntity(String::class.java)).thenReturn("Conflict")

        assertThrows<RuntimeException> { memberService.registerMember(req) }
    }

    @Test
    fun `registerMember sets member status to ACTIVE and no group`() {
        val req = registerReq()
        `when`(memberRepository.findByEmailAndDeletedAtIsNull(req.email)).thenReturn(null)
        `when`(memberRepository.findSimilarNames(req.firstName, req.lastName)).thenReturn(emptyList())
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }
        setupKeycloakMock()

        val result = memberService.registerMember(req)

        assertEquals(MemberStatus.ACTIVE, result.memberStatus)
        assertNull(result.group)
    }

    // --- getMemberProfile ---

    @Test
    fun `getMemberProfile throws ResponseStatusException when member not found`() {
        val keycloakSub = UUID.randomUUID().toString()
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSub)).thenReturn(null)
        `when`(memberRepository.findByEmailAndDeletedAtIsNull(keycloakSub)).thenReturn(null)

        assertThrows<ResponseStatusException> {
            memberService.getMemberProfile(keycloakSub)
        }
    }

    @Test
    fun `getMemberProfile returns MemberResponse by keycloakId`() {
        val keycloakSub = UUID.randomUUID().toString()
        val member = memberWithId(1L, "철수", "김")
        member.email = "found@example.com"
        member.city = "서울"
        member.keycloakId = keycloakSub
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSub)).thenReturn(member)

        val response = memberService.getMemberProfile(keycloakSub)

        assertEquals(member.publicId.toString(), response.publicId)
        assertEquals("철수", response.firstName)
        assertEquals("김", response.lastName)
        assertEquals("서울", response.city)
    }
}
