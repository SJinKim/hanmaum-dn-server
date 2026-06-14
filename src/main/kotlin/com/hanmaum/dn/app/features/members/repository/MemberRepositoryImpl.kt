package com.hanmaum.dn.app.features.members.repository

import com.hanmaum.dn.app.common.domainvalue.Baptism
import com.hanmaum.dn.app.common.domainvalue.Gender
import com.hanmaum.dn.app.common.domainvalue.MemberStatus
import com.hanmaum.dn.app.common.pii.PiiCryptoContext
import com.hanmaum.dn.app.common.pii.PiiProperties
import com.hanmaum.dn.app.features.members.domain.Member
import com.hanmaum.dn.app.features.statistics.api.v1.dto.ChartDataDto
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import java.time.Period

class MemberRepositoryImpl(
    private val entityManager: EntityManager,
    private val properties: PiiProperties,
) : MemberRepositorySecureQueries {
    override fun findByEmailAndDeletedAtIsNull(email: String): Member? =
        findByLookupHash("emailLookupHash", PiiCryptoContext.lookupHash(email))

    override fun findByKeycloakIdAndDeletedAtIsNull(keycloakId: String): Member? =
        findByLookupHash("keycloakLookupHash", PiiCryptoContext.lookupHash(keycloakId))

    override fun findActiveMembers(
        search: String?,
        status: MemberStatus?,
        baptism: Baptism?,
        pageable: Pageable,
    ): Page<Member> {
        val normalizedSearch = search?.takeIf(String::isNotBlank)?.let(PiiCryptoContext::normalize)
        val filtered =
            activeMembers(status)
                .asSequence()
                .filter { baptism == null || it.baptism == baptism }
                .filter { member ->
                    normalizedSearch == null ||
                        sequenceOf(member.lastName, member.firstName, member.email)
                            .filterNotNull()
                            .map(PiiCryptoContext::normalize)
                            .any { normalizedSearch in it }
                }.sortedWith(
                    compareBy<Member>(
                        { PiiCryptoContext.normalize(it.lastName) },
                        { PiiCryptoContext.normalize(it.firstName) },
                        { it.publicId },
                    ),
                ).toList()

        enforceInMemoryLimit(filtered.size)
        val start = pageable.offset.toInt().coerceAtMost(filtered.size)
        val end = (start + pageable.pageSize).coerceAtMost(filtered.size)
        return PageImpl(filtered.subList(start, end), pageable, filtered.size.toLong())
    }

    override fun findSimilarNames(
        firstName: String,
        lastName: String,
    ): List<Member> {
        val normalizedFirstName = normalizeName(firstName)
        val normalizedLastName = normalizeName(lastName)
        return activeMembers()
            .filter {
                normalizeName(it.firstName) == normalizedFirstName &&
                    normalizeName(it.lastName) == normalizedLastName
            }
    }

    override fun countNewMembersYtd(): Long = activeMembers().count { it.registrationDate?.year == LocalDate.now().year }.toLong()

    override fun getAverageAge(): Double {
        val ages = activeMembers().mapNotNull { it.birthDate?.let(::age) }
        return if (ages.isEmpty()) 0.0 else ages.average()
    }

    override fun getCityDistribution(): List<ChartDataDto> =
        activeMembers()
            .mapNotNull(Member::city)
            .filter(String::isNotBlank)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { ChartDataDto(it.key, it.value.toLong()) }

    override fun getAgeGroupsNative(): List<Array<Any>> =
        activeMembers()
            .mapNotNull { it.birthDate?.let(::age) }
            .mapNotNull(::ageGroup)
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
            .map { (label, count) -> arrayOf(label, count.toLong()) }

    override fun getGenderDistribution(): List<ChartDataDto> =
        activeMembers()
            .groupingBy { it.gender }
            .eachCount()
            .map { (gender, count) ->
                ChartDataDto(
                    label =
                        when (gender) {
                            Gender.M -> "형제"
                            Gender.F -> "자매"
                            null -> "Unbekannt"
                        },
                    value = count.toLong(),
                )
            }

    override fun findByEmail(email: String?): Member? = email?.let(::findByEmailAndDeletedAtIsNull)

    override fun findAllByFirstNameAndLastName(
        firstName: String,
        lastName: String,
    ): List<Member> = findSimilarNames(firstName, lastName)

    private fun findByLookupHash(
        property: String,
        lookupHash: String?,
    ): Member? {
        if (lookupHash == null) {
            return null
        }
        return entityManager
            .createQuery(
                """
                SELECT m FROM Member m
                WHERE m.$property = :lookupHash
                  AND m.deletedAt IS NULL
                """.trimIndent(),
                Member::class.java,
            ).setParameter("lookupHash", lookupHash)
            .resultList
            .singleOrNull()
    }

    private fun activeMembers(status: MemberStatus? = null): List<Member> {
        val query =
            entityManager.createQuery(
                """
                SELECT DISTINCT m FROM Member m
                LEFT JOIN FETCH m.group
                WHERE m.deletedAt IS NULL
                  AND (:status IS NULL OR m.memberStatus = :status)
                """.trimIndent(),
                Member::class.java,
            )
        query.setParameter("status", status)
        val members = query.resultList
        enforceInMemoryLimit(members.size)
        return members
    }

    private fun enforceInMemoryLimit(count: Int) {
        check(count <= properties.maxInMemoryMembers) {
            "Encrypted member query exceeds app.pii.max-in-memory-members=${properties.maxInMemoryMembers}."
        }
    }

    private fun normalizeName(value: String): String =
        PiiCryptoContext
            .normalize(value)
            .replace("-", "")
            .replace(" ", "")

    private fun age(birthDate: LocalDate): Int = Period.between(birthDate, LocalDate.now()).years

    private fun ageGroup(age: Int): String? =
        when (age) {
            in 16..19 -> "10대"
            in 20..29 -> "20대"
            in 30..39 -> "30대"
            in 40..Int.MAX_VALUE -> "40대+"
            else -> null
        }
}
