package com.hanmaum.dn.app.features.members.api

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import com.hanmaum.dn.app.features.members.api.v1.dto.CreateMemberRequest
import com.hanmaum.dn.app.features.members.api.v1.dto.UpdateMemberRequest
import com.hanmaum.dn.app.features.members.domain.Member
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class MemberMappersTest {

    private fun memberWithId(id: Long, firstName: String = "철수", lastName: String = "김"): Member {
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
    fun `toEntity maps invalid baptism to UNBAPTIZED as fallback`() {
        val entity = CreateMemberRequest(lastName = "김", firstName = "철수", baptism = "INVALID").toEntity()
        assertEquals(Baptism.UNBAPTIZED, entity.baptism)
    }

    // --- CreateMemberRequest.toEntity() field mapping ---

    @Test
    fun `toEntity maps all fields correctly`() {
        val req = CreateMemberRequest(
            lastName = "김",
            firstName = "철수",
            discriminator = "A",
            gender = "M",
            baptism = "UNBAPTIZED",
            birthDate = LocalDate.of(1995, 5, 15),
            phoneNumber = "010-1234-5678",
            email = "test@example.com",
            street = "테스트로 1",
            zipCode = "12345",
            city = "서울",
            role = "청년부원",
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
        assertEquals("테스트로 1", entity.street)
        assertEquals("12345", entity.zipCode)
        assertEquals("서울", entity.city)
        assertEquals("청년부원", entity.role)
    }

    // --- Member.updateForm() ---

    @Test
    fun `updateForm updates all fields`() {
        val member = Member(lastName = "김", firstName = "철수")
        val req = UpdateMemberRequest(
            lastName = "이",
            firstName = "영희",
            discriminator = "B",
            gender = "F",
            baptism = "UNBAPTIZED",
            phoneNumber = "010-9999-8888",
            email = "updated@example.com",
            street = "새 주소",
            zipCode = "99999",
            city = "부산",
            memberStatus = "PENDING",
            role = "리더",
        )
        member.updateForm(req)

        assertEquals("이", member.lastName)
        assertEquals("영희", member.firstName)
        assertEquals("B", member.discriminator)
        assertEquals(Gender.F, member.gender)
        assertEquals(Baptism.UNBAPTIZED, member.baptism)
        assertEquals("010-9999-8888", member.phoneNumber)
        assertEquals("updated@example.com", member.email)
        assertEquals("새 주소", member.street)
        assertEquals("99999", member.zipCode)
        assertEquals("부산", member.city)
        assertEquals(MemberStatus.PENDING, member.memberStatus)
        assertEquals("리더", member.role)
    }

    @Test
    fun `updateForm maps invalid memberStatus to ACTIVE as fallback`() {
        val member = Member(lastName = "김", firstName = "철수", memberStatus = MemberStatus.PENDING)
        val req = UpdateMemberRequest(lastName = "김", firstName = "철수", memberStatus = "GIBBERISH")
        member.updateForm(req)
        assertEquals(MemberStatus.ACTIVE, member.memberStatus)
    }

    @Test
    fun `updateForm maps valid lowercase memberStatus`() {
        val member = Member(lastName = "김", firstName = "철수", memberStatus = MemberStatus.ACTIVE)
        val req = UpdateMemberRequest(lastName = "김", firstName = "철수", memberStatus = "pending")
        member.updateForm(req)
        assertEquals(MemberStatus.PENDING, member.memberStatus)
    }

    // --- Member.toDto() ---

    @Test
    fun `toDto maps publicId as string id`() {
        val member = Member(lastName = "박", firstName = "준호", memberStatus = MemberStatus.ACTIVE)
        val dto = member.toDto()
        assertEquals(member.publicId.toString(), dto.id)
    }

    @Test
    fun `toDto maps all basic fields`() {
        val member = Member(
            lastName = "박",
            firstName = "준호",
            gender = Gender.M,
            memberStatus = MemberStatus.ACTIVE,
            city = "서울",
            role = "팀장",
        )
        val dto = member.toDto()

        assertEquals("박", dto.lastName)
        assertEquals("준호", dto.firstName)
        assertEquals("M", dto.gender)
        assertEquals("ACTIVE", dto.memberStatus)
        assertEquals("서울", dto.city)
        assertEquals("팀장", dto.role)
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
    fun `toResponse throws IllegalStateException when id is null`() {
        val member = Member(lastName = "김", firstName = "철수")
        assertThrows<IllegalStateException> { member.toResponse() }
    }

    @Test
    fun `toResponse maps all fields`() {
        val member = memberWithId(42L, "철수", "김")
        member.email = "test@example.com"
        member.memberStatus = MemberStatus.ACTIVE
        member.role = "청년부원"
        member.city = "서울"

        val response = member.toResponse()

        assertEquals(42L, response.id)
        assertEquals("철수", response.firstName)
        assertEquals("김", response.lastName)
        assertEquals("test@example.com", response.email)
        assertEquals(MemberStatus.ACTIVE, response.status)
        assertEquals("청년부원", response.role)
        assertEquals("서울", response.city)
        assertNull(response.groupName)
    }

    @Test
    fun `toResponse maps null city to empty string`() {
        val member = memberWithId(1L)
        member.city = null
        assertEquals("", member.toResponse().city)
    }

    @Test
    fun `toResponse maps null role to empty string`() {
        val member = memberWithId(1L)
        member.role = null
        assertEquals("", member.toResponse().role)
    }

    @Test
    fun `toResponse includes group name`() {
        val group = ChurchGroup(name = "다니엘조")
        val member = memberWithId(1L)
        member.group = group
        assertEquals("다니엘조", member.toResponse().groupName)
    }
}
