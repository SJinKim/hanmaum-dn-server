package com.hanmaum.dn.app.features.members.repository

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.statistics.api.v1.dto.ChartDataDto
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface MemberRepository :
    JpaRepository<Member, Long>,
    MemberRepositorySecureQueries {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<Member>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<Member>

    fun findByEmailLookupHashAndDeletedAtIsNull(emailLookupHash: String): Member?

    fun findByKeycloakLookupHashAndDeletedAtIsNull(keycloakLookupHash: String): Member?

    fun findAllByDeletedAtIsNull(): List<Member>

    fun findAllByMemberStatusAndDeletedAtIsNull(memberStatus: MemberStatus): List<Member>

    fun countByDeletedAtIsNull(): Long

    fun countByMemberStatusAndDeletedAtIsNull(memberStatus: MemberStatus): Long

    fun findAllByDeleteEntryAtLessThanEqualAndDeletedAtIsNotNull(deleteEntryAt: Instant): List<Member>

    fun findByPublicId(publicId: UUID): Optional<Member>

    /** Locked like [findForUpdateByPublicIdAndDeletedAtIsNull], for a restore of a soft-deleted member. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.publicId = :publicId")
    fun findForUpdateByPublicId(
        @Param("publicId") publicId: UUID,
    ): Optional<Member>

    /** Row-locks the members in id order, the lock order of the 장기결석자 writers (HDN-379). */
    @Query(value = "SELECT id FROM members WHERE id IN (:ids) ORDER BY id FOR UPDATE", nativeQuery = true)
    fun lockIdsForUpdate(
        @Param("ids") ids: Collection<Long>,
    ): List<Long>

    /** Raises the absence counter of a member locked by [lockIdsForUpdate]; stale editor tokens stop matching. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE members SET absence_revision = absence_revision + 1 WHERE id = :id", nativeQuery = true)
    fun bumpAbsenceRevision(
        @Param("id") id: Long,
    ): Int

    /** Members with their current 순 in one query, for list enrichment without N+1. */
    @Query("SELECT m FROM Member m LEFT JOIN FETCH m.group WHERE m.id IN :ids")
    fun findAllWithGroupByIdIn(
        @Param("ids") ids: Collection<Long>,
    ): List<Member>

    fun findAllByKeycloakLookupHashInAndDeletedAtIsNull(keycloakLookupHashes: Collection<String>): List<Member>
}

interface MemberRepositorySecureQueries {
    fun findByEmailAndDeletedAtIsNull(email: String): Member?

    fun findByEmailAndDeletedAtIsNullForUpdate(email: String): Member?

    fun findByKeycloakIdAndDeletedAtIsNull(keycloakId: String): Member?

    fun findByKeycloakIdAndDeletedAtIsNullForUpdate(keycloakId: String): Member?

    fun findActiveMembers(
        search: String?,
        status: MemberStatus?,
        baptism: Baptism?,
        pageable: Pageable,
    ): Page<Member>

    /**
     * Loads the encrypted member fields needed by the admin list before its related-record
     * filters and display-value sort are applied in the service layer.
     * Without a status filter only non-deleted members are returned; [MemberStatus.DELETED]
     * returns the soft-deleted members instead.
     */
    fun findActiveMembers(
        search: String?,
        status: MemberStatus?,
        baptism: Baptism?,
    ): List<Member>

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
