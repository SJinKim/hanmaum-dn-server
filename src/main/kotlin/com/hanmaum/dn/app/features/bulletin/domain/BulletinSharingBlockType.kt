package com.hanmaum.dn.app.features.bulletin.domain

/** Kind of one block in 설교 나눔; only SCRIPTURE carries a reference. */
enum class BulletinSharingBlockType {
    HEADING,
    PARAGRAPH,
    SCRIPTURE,
    QUESTION,
}
