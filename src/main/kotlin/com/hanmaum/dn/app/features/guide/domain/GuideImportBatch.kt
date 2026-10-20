package com.hanmaum.dn.app.features.guide.domain

import com.hanmaum.dn.app.common.jpa.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "guide_import_batches")
class GuideImportBatch(
    @Column(name = "source_sha256", nullable = false, length = 64) val sourceSha256: String,
    @Column(name = "mapping_version", nullable = false, length = 40) val mappingVersion: String,
) : BaseEntity() {
    @Column(name = "rolled_back_at")
    var rolledBackAt: Instant? = null
}
