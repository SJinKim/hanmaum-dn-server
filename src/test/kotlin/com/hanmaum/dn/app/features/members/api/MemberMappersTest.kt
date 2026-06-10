package com.hanmaum.dn.app.features.members.api

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.domain.Member
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class MemberMappersTest {
    private fun memberWithId(
        id: Long,
        firstName: String = "철수",
        lastName: String = "김",
    ): Member {
        val m = Member(lastName = lastName, firstName = firstName)
        m.id = id
        return m
    }

    // --- mapGender via CreateMemberRequest.toEntity() ---

    @Test
    fun `toEntity maps gender M`() {
        val entity = CreateMemberRequest(lastName = "김", firstName = "철수", gender = "M").toEntity()
        assertEquals(Gender.M, entity.gender)
    }

    @Test
    fun `toEntity maps gender MALE`() {
        val entity = CreateMemberRequest(lastName = "김", firstName = "철수", gender = "MALE").toEntity()
        assertEquals(Gender.M, entity.gender)
    }

    @Test
    fun `toEntity maps gender korean 남`() {
        val entity = CreateMemberRequest(lastName = "김", firstName = "철수", gender = "남").toEntity()
        assertEquals(Gender.M, entity.gender)
    }

    @Test
    fun `toEntity maps gender F`() {
        val entity = CreateMemberRequest(lastName = "이", firstName = "영희", gender = "F").toEntity()
        assertEquals(Gender.F, entity.gender)
    }

    @Test
    fun `toEntity maps gender FEMALE`() {
        val entity = CreateMemberRequest(lastName = "이", firstName = "영희", gender = "FEMALE").toEntity()
        assertEquals(Gender.F, entity.gender)
    }

    @Test
    fun `toEntity maps gender korean 여`() {
        val entity = CreateMemberRequest(lastName = "이", firstName = "영희", gender = "여").toEntity()
        assertEquals(Gender.F, entity.gender)
    }

    @Test
    fun `toEntity maps unknown gender to null`() {
        val entity = CreateMemberRequest(lastName = "박", firstName = "민준", gender = "UNKNOWN").toEntity()
        assertNull(entity.gender)
    }

    @Test
    fun `toEntity maps null gender to null`() {
        val entity = CreateMemberRequest(lastName = "박", firstName = "민준", gender = null).toEntity()
        assertNull(entity.gender)
    }

    // --- mapBaptism via CreateMemberRequest.toEntity() ---

    @Test
    fun `toEntity maps null baptism to null`() {
        val entity = CreateMemberRequest(lastName = "김", firstName = "철수", baptism = null).toEntity()
        assertNull(entity.baptism)
    }

    @Test
    fun `toEntity maps blank baptism to null`() {
        val entity = CreateMemberRequest(lastName = "김", firstName = "철수", baptism = "").toEntity()
        assertNull(entity.baptism)
    }

    @Test
    fun `toEntity maps valid baptism UNBAPTIZED`() {
        val entity = CreateMemberRequest(lastName = "김", firstName = "철수", baptism = "UNBAPTIZED").toEntity()
        assertEquals(Baptism.UNBAPTIZED, entity.baptism)
    }

    @Test
    fun `toEntity maps invalid baptism to null`() {
        val entity = CreateMemberRequest(lastName = "김", firstName = "철수", baptism = "INVALID").toEntity()
        assertNull(entity.baptism) // invalid enum value → null (no silent fallback)
    }

    // --- CreateMemberRequest.toEntity() field mapping ---

    @Test
    fun `toEntity maps all fields correctly`() {
        val req =
            CreateMemberRequest(
                lastName = "김",
                firstName = "철수",
                discriminator = "A",
                gender = "M",
                baptism = "UNBAPTIZED",
                birthDate = LocalDate.of(1995, 5, 15),
                phoneNumber = "010-1234-5678",
                email = "test@example.com",
                street = "테스트로",
                houseNumber = "1",
                zipCode = "12345",
                city = "서울",
                churchRole = "청년부원",
            )
        val entity = req.toEntity()

        assertEquals("김", entity.lastName)
        assertEquals("철수", entity.firstName)
        assertEquals("A", entity.discriminator)
        assertEquals(Gender.M, entity.gender)
        assertEquals(Baptism.UNBAPTIZED, entity.baptism)
        assertEquals(LocalDate.of(1995, 5, 15), entity.birthDate)
        assertEquals("010-1234-5678", entity.phoneNumber)
        assertEquals("test@example.com", entity.email)
        assertEquals("테스트로", entity.street)
        assertEquals("1", entity.houseNumber)
        assertEquals("12345", entity.zipCode)
        assertEquals("서울", entity.city)
        assertEquals("청년부원", entity.churchRole)
    }

    // --- Member.applyPatch() ---

    @Test
    fun `applyPatch updates provided fields only`() {
        val member = Member(lastName = "김", firstName = "철수")
        val req =
            UpdateMemberRequest(
                lastName = "이",
                firstName = "영희",
                discriminator = "B",
                gender = "F",
                baptism = "UNBAPTIZED",
                phoneNumber = "010-9999-8888",
                email = "updated@example.com",
                street = "새 주소",
                houseNumber = "12a",
                zipCode = "99999",
                city = "부산",
                memberStatus = "ACTIVE",
                churchRole = "리더",
            )
        member.applyPatch(req)

        assertEquals("이", member.lastName)
        assertEquals("영희", member.firstName)
        assertEquals("B", member.discriminator)
        assertEquals(Gender.F, member.gender)
        assertEquals(Baptism.UNBAPTIZED, member.baptism)
        assertEquals("010-9999-8888", member.phoneNumber)
        assertEquals("updated@example.com", member.email)
        assertEquals("새 주소", member.street)
        assertEquals("12a", member.houseNumber)
        assertEquals("99999", member.zipCode)
        assertEquals("부산", member.city)
        assertEquals(MemberStatus.ACTIVE, member.memberStatus)
        assertEquals("리더", member.churchRole)
    }

    @Test
    fun `applyPatch throws IllegalArgumentException for unknown memberStatus`() {
        val member = Member(lastName = "김", firstName = "철수")
        val req = UpdateMemberRequest(memberStatus = "GIBBERISH")
        assertThrows<IllegalArgumentException> { member.applyPatch(req) }
    }

    @Test
    fun `applyPatch throws IllegalStateException when trying to set DELETED via patch`() {
        val member = Member(lastName = "김", firstName = "철수")
        val req = UpdateMemberRequest(memberStatus = "DELETED")
        assertThrows<IllegalStateException> { member.applyPatch(req) }
    }

    @Test
    fun `applyPatch throws IllegalStateException when member is already DELETED`() {
        val member = Member(lastName = "김", firstName = "철수", memberStatus = MemberStatus.DELETED)
        val req = UpdateMemberRequest(lastName = "이")
        assertThrows<IllegalStateException> { member.applyPatch(req) }
    }

    @Test
    fun `applyPatch maps valid lowercase memberStatus`() {
        val member = Member(lastName = "김", firstName = "철수", memberStatus = MemberStatus.ACTIVE)
        val req = UpdateMemberRequest(memberStatus = "inactive")
        member.applyPatch(req)
        assertEquals(MemberStatus.INACTIVE, member.memberStatus)
    }

    @Test
    fun `applyPatch does not change fields when corresponding request field is null`() {
        val member = Member(lastName = "김", firstName = "철수", memberStatus = MemberStatus.ACTIVE)
        member.city = "서울"
        val req = UpdateMemberRequest() // all null
        member.applyPatch(req)
        assertEquals("서울", member.city) // unchanged
        assertEquals("김", member.lastName) // unchanged
    }

    // --- Member.toDto() ---

    @Test
    fun `toDto maps publicId as string`() {
        val member = Member(lastName = "박", firstName = "준호", memberStatus = MemberStatus.ACTIVE)
        val dto = member.toDto()
        assertEquals(member.publicId.toString(), dto.publicId)
    }

    @Test
    fun `toDto maps all basic fields`() {
        val member =
            Member(
                lastName = "박",
                firstName = "준호",
                gender = Gender.M,
                memberStatus = MemberStatus.ACTIVE,
                street = "Hauptstraße",
                houseNumber = "5",
                zipCode = "12345",
                city = "서울",
                churchRole = "팀장",
            )
        val dto = member.toDto()

        assertEquals("박", dto.lastName)
        assertEquals("준호", dto.firstName)
        assertEquals("M", dto.gender)
        assertEquals("ACTIVE", dto.memberStatus)
        assertEquals("Hauptstraße", dto.street)
        assertEquals("5", dto.houseNumber)
        assertEquals("12345", dto.zipCode)
        assertEquals("서울", dto.city)
        assertEquals("팀장", dto.churchRole)
        assertNull(dto.groupName)
    }

    @Test
    fun `toDto includes group name when group assigned`() {
        val group = ChurchGroup(name = "다니엘")
        val member = Member(lastName = "최", firstName = "소영", memberStatus = MemberStatus.ACTIVE)
        member.group = group
        val dto = member.toDto()
        assertEquals("다니엘", dto.groupName)
    }

    @Test
    fun `toDto maps gender null to null`() {
        val member = Member(lastName = "김", firstName = "철수", memberStatus = MemberStatus.ACTIVE, gender = null)
        assertNull(member.toDto().gender)
    }

    // --- Member.toResponse() ---

    @Test
    fun `toResponse uses publicId not internal id`() {
        val member = memberWithId(42L, "철수", "김")
        val response = member.toResponse()
        assertEquals(member.publicId.toString(), response.publicId)
    }

    @Test
    fun `toResponse maps all fields`() {
        val member = memberWithId(42L, "철수", "김")
        member.email = "test@example.com"
        member.memberStatus = MemberStatus.ACTIVE
        member.churchRole = "청년부원"
        member.street = "Hauptstraße"
        member.houseNumber = "5"
        member.zipCode = "12345"
        member.city = "서울"

        val response = member.toResponse()

        assertEquals(member.publicId.toString(), response.publicId)
        assertEquals("철수", response.firstName)
        assertEquals("김", response.lastName)
        assertEquals("test@example.com", response.email)
        assertEquals(MemberStatus.ACTIVE, response.status)
        assertEquals("청년부원", response.churchRole)
        assertEquals("Hauptstraße", response.street)
        assertEquals("5", response.houseNumber)
        assertEquals("12345", response.zipCode)
        assertEquals("서울", response.city)
        assertNull(response.groupName)
    }

    @Test
    fun `toResponse maps null city to null`() {
        val member = memberWithId(1L)
        member.city = null
        assertNull(member.toResponse().city)
    }

    @Test
    fun `toResponse maps null churchRole to null`() {
        val member = memberWithId(1L)
        member.churchRole = null
        assertNull(member.toResponse().churchRole)
    }

    @Test
    fun `toResponse includes group name`() {
        val group = ChurchGroup(name = "다니엘조")
        val member = memberWithId(1L)
        member.group = group
        assertEquals("다니엘조", member.toResponse().groupName)
    }
}
