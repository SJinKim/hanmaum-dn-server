package com.hanmaum.dn.app.features.newcomers.service

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import java.util.UUID

@ConfigurationProperties("app.newcomer-import")
data class NewcomerImportProperties(
    val enabled: Boolean = false,
    val file: Path? = null,
    val layout: NewcomerImportLayout = NewcomerImportLayout.SPREADSHEET_A,
    val dryRun: Boolean = true,
    val decisions: Map<Int, UUID> = emptyMap(),
    val caregivers: Map<String, UUID> = emptyMap(),
)

enum class NewcomerImportLayout { SPREADSHEET_A, SPREADSHEET_B, GOOGLE_FORMS }
