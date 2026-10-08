package com.hanmaum.dn.app.features.bulletin.domain

/** Lifecycle of a 주보 edition; only PUBLISHED is visible to members. */
enum class BulletinStatus {
    DRAFT,
    PUBLISHED,
    WITHDRAWN,
}
