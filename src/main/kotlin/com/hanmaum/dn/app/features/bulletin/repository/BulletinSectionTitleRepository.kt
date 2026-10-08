package com.hanmaum.dn.app.features.bulletin.repository

import com.hanmaum.dn.app.features.bulletin.domain.BulletinSectionKey
import com.hanmaum.dn.app.features.bulletin.domain.BulletinSectionTitle
import org.springframework.data.jpa.repository.JpaRepository

interface BulletinSectionTitleRepository : JpaRepository<BulletinSectionTitle, BulletinSectionKey>
