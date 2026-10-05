package com.hanmaum.dn.app.features.members.domain

/**
 * How a member record was created. Set once on insert and never changed: claiming an app
 * account later does not turn a [MANUAL] or [NEWCOMER_FORM] member into [APP].
 *
 * NEWCOMER_FORM covers every 새가족 intake (public form, 빠른 등록, import).
 * The values are mirrored by ck_members_origin — adding one needs a migration.
 */
enum class MemberOrigin {
    MANUAL,
    NEWCOMER_FORM,
    APP,
}
