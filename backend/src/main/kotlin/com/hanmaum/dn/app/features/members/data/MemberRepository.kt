package com.hanmaum.dn.app.features.members.data

import com.hanmaum.dn.app.features.members.domain.Member
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberRepository : JpaRepository<Member, Long> {
    fun findByKoreanNameContaining(name: String): List<Member>
}