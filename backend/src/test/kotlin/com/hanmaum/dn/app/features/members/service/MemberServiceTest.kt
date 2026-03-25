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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.representations.idm.UserRepresentation
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import jakarta.ws.rs.core.Response

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

    private fun memberWithId(id: Long, firstName: String = "철수", lastName: String = "김"): Member {
        val m = Member(lastName = lastName, firstName = firstName)
        m.id = id
        return m
    }

    private fun group(id: Long, name: String = "다니엘조"): ChurchGroup {
        val g = ChurchGroup(name = name)
        g.id = id
        return g
    }

    private fun setupKeycloakMock(statusCode: Int = 201) {
        `when`(keycloak.realm("test-realm")).thenReturn(realmResource)
        `when`(realmResource.users()).thenReturn(usersResource)
        `when`(usersResource.create(any(UserRepresentation::class.java))).thenReturn(kcResponse)
        `when`(kcResponse.status).thenReturn(statusCode)
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

    // --- getMember ---

    @Test
    fun `getMember throws EntityNotFoundException for invalid UUID string`() {
        assertThrows<EntityNotFoundException> {
            memberService.getMember("not-a-uuid")
        }
    }

    @Test
    fun `getMember throws EntityNotFoundException when member not found`() {
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> {
            memberService.getMember(UUID.randomUUID().toString())
        }
    }

    @Test
    fun `getMember returns member when found`() {
        val member = memberWithId(1L)
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(member))

        val result = memberService.getMember(member.publicId.toString())

        assertEquals(member, result)
    }

    // --- getAllMembers ---

    @Test
    fun `getAllMembers delegates to repository`() {
        val members = listOf(memberWithId(1L), memberWithId(2L))
        `when`(memberRepository.findAll()).thenReturn(members)

        val result = memberService.getAllMembers()

        assertEquals(members, result)
    }

    // --- createMember ---

    @Test
    fun `createMember saves member without group when groupId is null`() {
        val req = CreateMemberRequest(lastName = "김", firstName = "철수")
        `when`(memberRepository.save(any(Member::class.java))).thenAnswer { it.arguments[0] }

        val result = memberService.createMember(req)

        assertNull(result.group)
        verify(churchGroupRepository, never()).findById(anyLong())
    }

    @Test
    fun `createMember assigns group when groupId provided`() {
        val grp = group(5L, "다니엘조")
        val req = CreateMemberRequest(lastName = "김", firstName = "철수", groupId = 5L)
        `when`(churchGroupRepository.findById(5L)).thenReturn(Optional.of(grp))
        `when`(memberRepository.save(any(Member::class.java))).thenAnswer { it.arguments[0] }

        val result = memberService.createMember(req)

        assertEquals("다니엘조", result.group?.name)
    }

    @Test
    fun `createMember throws EntityNotFoundException when group not found`() {
        val req = CreateMemberRequest(lastName = "김", firstName = "철수", groupId = 99L)
        `when`(churchGroupRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows<EntityNotFoundException> { memberService.createMember(req) }
    }

    // --- updateMember ---

    @Test
    fun `updateMember does not call groupRepository when groupId is null`() {
        val member = memberWithId(1L)
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(member))
        `when`(memberRepository.save(any(Member::class.java))).thenAnswer { it.arguments[0] }

        memberService.updateMember(member.publicId.toString(), UpdateMemberRequest(
            lastName = "김", firstName = "철수", memberStatus = "ACTIVE", groupId = null
        ))

        verify(churchGroupRepository, never()).findById(anyLong())
    }

    @Test
    fun `updateMember updates group when groupId changes`() {
        val oldGroup = group(1L, "구 그룹")
        val newGroup = group(2L, "새 그룹")
        val member = memberWithId(10L)
        member.group = oldGroup
        val req = UpdateMemberRequest(lastName = "김", firstName = "철수", memberStatus = "ACTIVE", groupId = 2L)

        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(member))
        `when`(churchGroupRepository.findById(2L)).thenReturn(Optional.of(newGroup))
        `when`(memberRepository.save(any(Member::class.java))).thenAnswer { it.arguments[0] }

        val result = memberService.updateMember(member.publicId.toString(), req)

        assertEquals("새 그룹", result.group?.name)
    }

    @Test
    fun `updateMember does not call groupRepository when groupId is same as current`() {
        val grp = group(1L, "기존 그룹")
        val member = memberWithId(10L)
        member.group = grp
        val req = UpdateMemberRequest(lastName = "김", firstName = "철수", memberStatus = "ACTIVE", groupId = 1L)

        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(member))
        `when`(memberRepository.save(any(Member::class.java))).thenAnswer { it.arguments[0] }

        memberService.updateMember(member.publicId.toString(), req)

        verify(churchGroupRepository, never()).findById(anyLong())
    }

    // --- softDeleteMember ---

    @Test
    fun `softDeleteMember sets deletedAt and status to DELETED`() {
        val member = memberWithId(1L)
        `when`(memberRepository.findByPublicId(any(UUID::class.java))).thenReturn(Optional.of(member))
        `when`(memberRepository.save(any(Member::class.java))).thenAnswer { it.arguments[0] }

        memberService.softDeleteMember(member.publicId.toString())

        assertNotNull(member.deletedAt)
        assertEquals(MemberStatus.DELETED, member.memberStatus)
    }

    // --- registerMember ---

    @Test
    fun `registerMember throws IllegalArgumentException when email already exists`() {
        val existing = memberWithId(1L)
        `when`(memberRepository.findByEmail("test@example.com")).thenReturn(existing)

        assertThrows<IllegalArgumentException> {
            memberService.registerMember(registerReq(email = "test@example.com"))
        }
    }

    @Test
    fun `registerMember assigns null discriminator for first member with that name`() {
        val req = registerReq()
        `when`(memberRepository.findByEmail(req.email)).thenReturn(null)
        `when`(memberRepository.findSimilarNames(req.firstName, req.lastName)).thenReturn(emptyList())
        `when`(memberRepository.save(any(Member::class.java))).thenAnswer { it.arguments[0] }
        setupKeycloakMock()

        val result = memberService.registerMember(req)

        assertNull(result.discriminator)
    }

    @Test
    fun `registerMember assigns discriminator A when one member with null discriminator exists`() {
        val req = registerReq(email = "new@example.com")
        val existingMember = Member(lastName = "김", firstName = "철수", discriminator = null)
        `when`(memberRepository.findByEmail(req.email)).thenReturn(null)
        `when`(memberRepository.findSimilarNames(req.firstName, req.lastName)).thenReturn(listOf(existingMember))
        `when`(memberRepository.save(any(Member::class.java))).thenAnswer { it.arguments[0] }
        setupKeycloakMock()

        val result = memberService.registerMember(req)

        assertEquals("A", result.discriminator)
    }

    @Test
    fun `registerMember assigns discriminator B when A is already taken`() {
        val req = registerReq(email = "newer@example.com")
        val e1 = Member(lastName = "김", firstName = "철수", discriminator = null)
        val e2 = Member(lastName = "김", firstName = "철수", discriminator = "A")
        `when`(memberRepository.findByEmail(req.email)).thenReturn(null)
        `when`(memberRepository.findSimilarNames(req.firstName, req.lastName)).thenReturn(listOf(e1, e2))
        `when`(memberRepository.save(any(Member::class.java))).thenAnswer { it.arguments[0] }
        setupKeycloakMock()

        val result = memberService.registerMember(req)

        assertEquals("B", result.discriminator)
    }

    @Test
    fun `registerMember assigns discriminator C when A and B are taken`() {
        val req = registerReq(email = "newest@example.com")
        val members = listOf(
            Member(lastName = "김", firstName = "철수", discriminator = null),
            Member(lastName = "김", firstName = "철수", discriminator = "A"),
            Member(lastName = "김", firstName = "철수", discriminator = "B"),
        )
        `when`(memberRepository.findByEmail(req.email)).thenReturn(null)
        `when`(memberRepository.findSimilarNames(req.firstName, req.lastName)).thenReturn(members)
        `when`(memberRepository.save(any(Member::class.java))).thenAnswer { it.arguments[0] }
        setupKeycloakMock()

        val result = memberService.registerMember(req)

        assertEquals("C", result.discriminator)
    }

    @Test
    fun `registerMember throws RuntimeException when Keycloak returns non-201`() {
        val req = registerReq()
        `when`(memberRepository.findByEmail(req.email)).thenReturn(null)
        `when`(memberRepository.findSimilarNames(req.firstName, req.lastName)).thenReturn(emptyList())
        `when`(memberRepository.save(any(Member::class.java))).thenAnswer { it.arguments[0] }
        setupKeycloakMock(statusCode = 409)
        `when`(kcResponse.readEntity(String::class.java)).thenReturn("Conflict")

        assertThrows<RuntimeException> { memberService.registerMember(req) }
    }

    @Test
    fun `registerMember sets member status to PENDING and no group`() {
        val req = registerReq()
        `when`(memberRepository.findByEmail(req.email)).thenReturn(null)
        `when`(memberRepository.findSimilarNames(req.firstName, req.lastName)).thenReturn(emptyList())
        `when`(memberRepository.save(any(Member::class.java))).thenAnswer { it.arguments[0] }
        setupKeycloakMock()

        val result = memberService.registerMember(req)

        assertEquals(MemberStatus.PENDING, result.memberStatus)
        assertNull(result.group)
    }

    // --- getMemberProfile ---

    @Test
    fun `getMemberProfile throws ResponseStatusException when member not found`() {
        `when`(memberRepository.findByEmail("unknown@example.com")).thenReturn(null)

        assertThrows<ResponseStatusException> {
            memberService.getMemberProfile("unknown@example.com")
        }
    }

    @Test
    fun `getMemberProfile returns MemberResponse when found`() {
        val member = memberWithId(1L, "철수", "김")
        member.email = "found@example.com"
        member.city = "서울"
        `when`(memberRepository.findByEmail("found@example.com")).thenReturn(member)

        val response = memberService.getMemberProfile("found@example.com")

        assertEquals(1L, response.id)
        assertEquals("철수", response.firstName)
        assertEquals("김", response.lastName)
        assertEquals("서울", response.city)
    }
}
