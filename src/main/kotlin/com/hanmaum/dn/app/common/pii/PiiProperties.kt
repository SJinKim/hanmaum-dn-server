package com.hanmaum.dn.app.common.pii

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.pii")
data class PiiProperties(
    var strict: Boolean = false,
    var localPlaintextEnabled: Boolean = false,
    var keyringPath: String = "",
    var activeKeyId: String = "v1",
    var encryptionKey: String = "",
    var indexKey: String = "",
    var legacyPlaintextReadEnabled: Boolean = true,
    var backfillEnabled: Boolean = true,
    var backfillBatchSize: Int = 200,
    var maxInMemoryMembers: Int = 5_000,
)
