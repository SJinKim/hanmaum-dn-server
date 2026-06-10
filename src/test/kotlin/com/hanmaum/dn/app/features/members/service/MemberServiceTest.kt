package com.hanmaum.dn.app.features.members.service

import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.groups.repository.ChurchGroupRepository
import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.MemberMinistryItem
import com.hanmaum.dn.app.features.members.api.v1.dto.RegisterMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.ReplaceMemberMinistriesRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMyProfileRequest
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.members.repository.MemberRepository
import com.hanmaum.dn.app.features.ministry.domain.Ministry
import com.hanmaum.dn.app.features.ministry.domain.MinistryAssignment
import com.hanmaum.dn.app.features.ministry.repository.MemberMinistryView
import com.hanmaum.dn.app.features.ministry.repository.MinistryAssignmentRepository
import com.hanmaum.dn.app.features.ministry.repository.MinistryRepository
import com.hanmaum.dn.app.features.training.repository.TrainingRepository
import com.hanmaum.dn.app.features.training.repository.UserTrainingRepository
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
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MemberServiceTest {
    @Mock private lateinit var memberRepository: MemberRepository

    @Mock private lateinit var churchGroupRepository: ChurchGroupRepository

    @Mock private lateinit var userTrainingRepository: UserTrainingRepository

    @Mock private lateinit var trainingRepository: TrainingRepository

    @Mock private lateinit var ministryAssignmentRepository: MinistryAssignmentRepository

    @Mock private lateinit var ministryRepository: MinistryRepository

    @Mock private lateinit var keycloak: Keycloak

    @Mock private lateinit var realmResource: RealmResource

    @Mock private lateinit var usersResource: UsersResource

    @Mock private lateinit var kcResponse: Response

    private lateinit var memberService: MemberService

    @BeforeEach
    fun setUp() {
        memberService =
            MemberService(
                memberRepository,
                churchGroupRepository,
                userTrainingRepository,
                trainingRepository,
                ministryAssignmentRepository,
                ministryRepository,
                keycloak,
                "test-realm",
            )
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
        street = "Hauptstraße",
        houseNumber = "12a",
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

    // --- replaceMemberMinistries ---

    @Test
    fun `replaceMemberMinistries deletes existing then inserts from request`() {
        val member = memberWithId(42L)
        val ministry =
            Ministry(
                name = "찬양팀",
                shortDescription = "찬양 사역팀",
                longDescription = "찬양으로 예배를 섬깁니다.",
            ).also { it.id = 1L }

        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(member.publicId))
            .thenReturn(Optional.of(member))
        `when`(ministryRepository.findByPublicIdAndDeletedAtIsNull(ministry.publicId))
            .thenReturn(Optional.of(ministry))
        `when`(userTrainingRepository.findByMemberId(42L)).thenReturn(emptyList())
        `when`(ministryAssignmentRepository.findByMemberId(42L)).thenReturn(emptyList())
        `when`(ministryAssignmentRepository.saveAll(any<List<MinistryAssignment>>()))
            .thenAnswer { it.arguments[0] }

        val request =
            ReplaceMemberMinistriesRequest(
                listOf(
                    MemberMinistryItem(
                        ministryPublicId = ministry.publicId.toString(),
                        startDate = LocalDate.of(2024, 3, 1),
                        endDate = null,
                        note = "악기",
                    ),
                ),
            )

        val result = memberService.replaceMemberMinistries(member.publicId, request)

        verify(ministryAssignmentRepository).deleteByMemberId(42L)
        verify(ministryAssignmentRepository).flush()
        verify(ministryAssignmentRepository).saveAll(any<List<MinistryAssignment>>())
        assertEquals(member.publicId.toString(), result.publicId)
    }

    // --- getMembers ---

    @Test
    fun `getMembers delegates to repository with pageable`() {
        val members = listOf(memberWithId(1L), memberWithId(2L))
        `when`(memberRepository.findActiveMembers(anyOrNull(), anyOrNull(), anyOrNull(), any<Pageable>()))
            .thenReturn(PageImpl(members))

        val result = memberService.getMembers(null, null, null, 0, 20)

        assertEquals(2, result.totalElements)
    }

    @Test
    fun `getMembers maps multiple active ministries sorted for a member`() {
        val member = memberWithId(1L)
        `when`(memberRepository.findActiveMembers(anyOrNull(), anyOrNull(), anyOrNull(), any<Pageable>()))
            .thenReturn(PageImpl(listOf(member)))
        `when`(ministryAssignmentRepository.findActiveByMemberIds(listOf(1L)))
            // Returned out of order on purpose so the assertion proves .sorted() runs.
            .thenReturn(listOf(MemberMinistryView(1L, "찬양팀"), MemberMinistryView(1L, "미디어팀")))

        val result = memberService.getMembers(null, null, null, 0, 20)

        val summary = result.content.single()
        assertEquals(listOf("미디어팀", "찬양팀"), summary.activeMinistries)
    }

    // --- createMember ---

    @Test
    fun `createMember saves member without group when groupPublicId is null`() {
        val req = CreateMemberRequest(lastName = "김", firstName = "철수")
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }

        val result = memberService.createMember(req)

        assertNull(result.groupName)
        verify(churchGroupRepository, never()).findByPublicIdAndDeletedAtIsNull(any<UUID>())
    }

    @Test
    fun `createMember assigns group when groupPublicId provided`() {
        val grp = group(5L, "다니엘조")
        val req = CreateMemberRequest(lastName = "김", firstName = "철수", groupPublicId = grp.publicId.toString())
        `when`(churchGroupRepository.findByPublicIdAndDeletedAtIsNull(grp.publicId)).thenReturn(Optional.of(grp))
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }

        val result = memberService.createMember(req)

        assertEquals("다니엘조", result.groupName)
    }

    @Test
    fun `createMember throws EntityNotFoundException when group not found`() {
        val missingId = UUID.randomUUID()
        val req = CreateMemberRequest(lastName = "김", firstName = "철수", groupPublicId = missingId.toString())
        `when`(churchGroupRepository.findByPublicIdAndDeletedAtIsNull(missingId)).thenReturn(Optional.empty())

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
    fun `updateMember does not call groupRepository when groupPublicId is null`() {
        val member = memberWithId(1L)
        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(member.publicId))
            .thenReturn(Optional.of(member))
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }

        memberService.updateMember(
            member.publicId,
            UpdateMemberRequest(lastName = "김", firstName = "철수", groupPublicId = null),
        )

        verify(churchGroupRepository, never()).findByPublicIdAndDeletedAtIsNull(any<UUID>())
    }

    @Test
    fun `updateMember updates group when groupPublicId changes`() {
        val oldGroup = group(1L, "구 그룹")
        val newGroup = group(2L, "새 그룹")
        val member = memberWithId(10L)
        member.group = oldGroup
        val req = UpdateMemberRequest(lastName = "김", firstName = "철수", groupPublicId = newGroup.publicId.toString())

        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(member.publicId))
            .thenReturn(Optional.of(member))
        `when`(churchGroupRepository.findByPublicIdAndDeletedAtIsNull(newGroup.publicId)).thenReturn(Optional.of(newGroup))
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }

        val result = memberService.updateMember(member.publicId, req)

        assertEquals("새 그룹", result.groupName)
    }

    @Test
    fun `updateMember does not call groupRepository when groupPublicId is same as current`() {
        val grp = group(1L, "기존 그룹")
        val member = memberWithId(10L)
        member.group = grp
        val req = UpdateMemberRequest(lastName = "김", firstName = "철수", groupPublicId = grp.publicId.toString())

        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(member.publicId))
            .thenReturn(Optional.of(member))
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }

        memberService.updateMember(member.publicId, req)

        verify(churchGroupRepository, never()).findByPublicIdAndDeletedAtIsNull(any<UUID>())
    }

    @Test
    fun `updateMember clears the group when groupPublicId is a blank string`() {
        val grp = group(1L, "기존 그룹")
        val member = memberWithId(10L)
        member.group = grp
        val req = UpdateMemberRequest(lastName = "김", firstName = "철수", groupPublicId = "")

        `when`(memberRepository.findByPublicIdAndDeletedAtIsNull(member.publicId))
            .thenReturn(Optional.of(member))
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }

        val result = memberService.updateMember(member.publicId, req)

        assertNull(result.groupName)
        assertNull(member.group)
        verify(churchGroupRepository, never()).findByPublicIdAndDeletedAtIsNull(any<UUID>())
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
    fun `registerMember sets member status to PENDING and no group`() {
        val req = registerReq()
        `when`(memberRepository.findByEmailAndDeletedAtIsNull(req.email)).thenReturn(null)
        `when`(memberRepository.findSimilarNames(req.firstName, req.lastName)).thenReturn(emptyList())
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }
        setupKeycloakMock()

        val result = memberService.registerMember(req)

        assertEquals(MemberStatus.PENDING, result.memberStatus)
        assertNull(result.group)
    }

    @Test
    fun `registerMember persists street and house number separately`() {
        val req = registerReq()
        `when`(memberRepository.findByEmailAndDeletedAtIsNull(req.email)).thenReturn(null)
        `when`(memberRepository.findSimilarNames(req.firstName, req.lastName)).thenReturn(emptyList())
        `when`(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }
        setupKeycloakMock()

        val result = memberService.registerMember(req)

        assertEquals("Hauptstraße", result.street)
        assertEquals("12a", result.houseNumber)
    }

    // --- getMemberProfile ---

    @Test
    fun `getMemberProfile throws ResponseStatusException when member not found`() {
        val keycloakSub = UUID.randomUUID().toString()
        val email = "notfound@example.com"
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSub)).thenReturn(null)
        `when`(memberRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(null)

        assertThrows<ResponseStatusException> {
            memberService.getMemberProfile(keycloakSub, email)
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

        val response = memberService.getMemberProfile(keycloakSub, "found@example.com")

        assertEquals(member.publicId.toString(), response.publicId)
        assertEquals("철수", response.firstName)
        assertEquals("김", response.lastName)
        assertEquals("서울", response.city)
    }

    @Test
    fun `getMemberProfile falls back to email lookup for legacy records`() {
        val keycloakSub = UUID.randomUUID().toString()
        val email = "legacy@example.com"
        val member = memberWithId(1L, "영희", "이")
        member.email = email
        member.city = "부산"
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSub)).thenReturn(null)
        `when`(memberRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(member)

        val response = memberService.getMemberProfile(keycloakSub, email)

        assertEquals(member.publicId.toString(), response.publicId)
        assertEquals("영희", response.firstName)
    }

    @Test
    fun `updateMyProfile updates address fields`() {
        val keycloakSub = UUID.randomUUID().toString()
        val member = memberWithId(1L)
        member.keycloakId = keycloakSub
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSub)).thenReturn(member)
        `when`(memberRepository.save(member)).thenReturn(member)

        val response =
            memberService.updateMyProfile(
                keycloakSubject = keycloakSub,
                email = null,
                request =
                    UpdateMyProfileRequest(
                        street = "Hauptstraße",
                        houseNumber = "12a",
                        zipCode = "10115",
                        city = "Berlin",
                    ),
            )

        assertEquals("Hauptstraße", response.street)
        assertEquals("12a", response.houseNumber)
        assertEquals("10115", response.zipCode)
        assertEquals("Berlin", response.city)
        verify(memberRepository).save(member)
    }

    @Test
    fun `updateMyProfile preserves address fields omitted from patch`() {
        val keycloakSub = UUID.randomUUID().toString()
        val member = memberWithId(1L)
        member.keycloakId = keycloakSub
        member.street = "Existing Street"
        member.houseNumber = "7"
        member.zipCode = "50667"
        member.city = "Köln"
        `when`(memberRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakSub)).thenReturn(member)
        `when`(memberRepository.save(member)).thenReturn(member)

        val response =
            memberService.updateMyProfile(
                keycloakSubject = keycloakSub,
                email = null,
                request = UpdateMyProfileRequest(phoneNumber = "+49 123"),
            )

        assertEquals("Existing Street", response.street)
        assertEquals("7", response.houseNumber)
        assertEquals("50667", response.zipCode)
        assertEquals("Köln", response.city)
        assertEquals("+49 123", response.phoneNumber)
    }
}
