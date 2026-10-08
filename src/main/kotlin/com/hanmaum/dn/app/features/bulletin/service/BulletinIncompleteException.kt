package com.hanmaum.dn.app.features.bulletin.service

/**
 * A 주보 that cannot be published yet: [fieldErrors] names each missing field.
 *
 * [message] is Korean and written for the admin; the dashboard may show it as it is.
 */
class BulletinIncompleteException(
    val fieldErrors: Map<String, String>,
) : RuntimeException("주보를 발행하려면 필수 항목을 모두 입력해야 합니다.")
