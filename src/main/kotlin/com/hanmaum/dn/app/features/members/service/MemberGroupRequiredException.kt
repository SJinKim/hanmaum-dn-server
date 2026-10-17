package com.hanmaum.dn.app.features.members.service

private const val MEMBER_GROUP_REQUIRED_MESSAGE = "순 배정은 필수입니다. 순을 배정해 주세요."

/**
 * An ACTIVE member would end up without a 순 (HDN-314).
 *
 * An ACTIVE member checks in and is counted per 순, so one without a 순 checks in and is
 * counted nowhere. [fieldErrors] names `groupPublicId`, so the dashboard can mark the field.
 */
class MemberGroupRequiredException : RuntimeException(MEMBER_GROUP_REQUIRED_MESSAGE) {
    val fieldErrors: Map<String, String> = mapOf("groupPublicId" to MEMBER_GROUP_REQUIRED_MESSAGE)
}
