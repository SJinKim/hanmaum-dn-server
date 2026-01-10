package com.hanmaum.dn.app.features.groups.repository

import com.hanmaum.dn.app.features.groups.domain.ChurchGroup
import org.springframework.data.jpa.repository.JpaRepository

interface ChurchGroupRepository : JpaRepository<ChurchGroup, Long> {

}