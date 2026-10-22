package com.hanmaum.dn.app.features.consents.repository

import com.hanmaum.dn.app.common.domainvalue.ConsentPurpose
import com.hanmaum.dn.app.features.consents.domain.MemberConsent
import org.springframework.data.jpa.repository.JpaRepository
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
}
