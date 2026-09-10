package com.hanmaum.dn.app.features.verses.repository

import com.hanmaum.dn.app.common.domainvalue.VerseRecordKind
import java.time.LocalDate

/** One mark, carrying its kind so both streaks can be read in a single query. */
data class VerseRecordMark(
    val recordDate: LocalDate,
    val kind: VerseRecordKind,
)

/** All-time total per kind, grouped in the database rather than counted once per kind. */
data class VerseRecordCount(
    val kind: VerseRecordKind,
    val total: Long,
)
