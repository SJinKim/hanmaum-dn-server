package com.hanmaum.dn.app.features.members.repository

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.statistics.api.v1.dto.ChartDataDto
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface MemberRepository :
    JpaRepository<Member, Long>,
    MemberRepositorySecureQueries {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<Member>

    fun findByEmailLookupHashAndDeletedAtIsNull(emailLookupHash: String): Member?

    fun findByKeycloakLookupHashAndDeletedAtIsNull(keycloakLookupHash: String): Member?

    fun findAllByDeletedAtIsNull(): List<Member>

    fun findAllByMemberStatusAndDeletedAtIsNull(memberStatus: MemberStatus): List<Member>

    fun countByDeletedAtIsNull(): Long

    fun findAllByDeleteEntryAtLessThanEqualAndDeletedAtIsNotNull(deleteEntryAt: Instant): List<Member>

    fun findByPublicId(publicId: UUID): Optional<Member>
}

interface MemberRepositorySecureQueries {
    fun findByEmailAndDeletedAtIsNull(email: String): Member?

    fun findByKeycloakIdAndDeletedAtIsNull(keycloakId: String): Member?

    fun findActiveMembers(
        search: String?,
        status: MemberStatus?,
        baptism: Baptism?,
        pageable: Pageable,
    ): Page<Member>

    fun findSimilarNames(
        firstName: String,
        lastName: String,
    ): List<Member>

    fun countNewMembersYtd(): Long

    fun getAverageAge(): Double

    fun getCityDistribution(): List<ChartDataDto>

    fun getAgeGroupsNative(): List<Array<Any>>

    fun getGenderDistribution(): List<ChartDataDto>

    fun findByEmail(email: String?): Member?

    fun findAllByFirstNameAndLastName(
        firstName: String,
        lastName: String,
    ): List<Member>
}
