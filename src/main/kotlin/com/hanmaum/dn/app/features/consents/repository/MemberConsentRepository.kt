package com.hanmaum.dn.app.features.consents.repository

import com.hanmaum.dn.app.common.domainvalue.ConsentPurpose
import com.hanmaum.dn.app.features.consents.domain.MemberConsent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MemberConsentRepository : JpaRepository<MemberConsent, Long> {
    /** The standing grant per purpose; at most one each, by uq_member_consent_standing. */
    @Query(
        """
        SELECT c FROM MemberConsent c
        WHERE c.member.id = :memberId AND c.endedAt IS NULL AND c.deletedAt IS NULL
        """,
    )
    fun findStanding(
        @Param("memberId") memberId: Long,
    ): List<MemberConsent>

    @Query(
        """
        SELECT c FROM MemberConsent c
        WHERE c.member.id = :memberId AND c.purpose = :purpose
          AND c.endedAt IS NULL AND c.deletedAt IS NULL
        """,
    )
    fun findStanding(
        @Param("memberId") memberId: Long,
        @Param("purpose") purpose: ConsentPurpose,
    ): MemberConsent?

    /**
     * Serialises grant and withdrawal per member. uq_member_consent_standing cannot do it alone:
     * with no standing row yet there is nothing to lock, so two parallel grants would both
     * insert and one would fail with an integrity error.
     */
    @Query(value = "SELECT m.id FROM members m WHERE m.id = :memberId FOR UPDATE", nativeQuery = true)
    fun lockMember(
        @Param("memberId") memberId: Long,
    ): Long?

    /**
     * Re-points every consent row of [fromMemberId] to [toMemberId], history included. Native
     * because member_id is not updatable through the entity: a grant belongs to the person
     * who gave it, and only an identity merge changes who that is.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        value = "UPDATE member_consents SET member_id = :toMemberId, updated_at = CURRENT_TIMESTAMP WHERE member_id = :fromMemberId",
        nativeQuery = true,
    )
    fun moveAll(
        @Param("fromMemberId") fromMemberId: Long,
        @Param("toMemberId") toMemberId: Long,
    ): Int
}
