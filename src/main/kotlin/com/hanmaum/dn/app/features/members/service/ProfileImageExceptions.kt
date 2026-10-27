package com.hanmaum.dn.app.features.members.service

private const val PROFILE_IMAGE_TOO_LARGE_MESSAGE = "프로필 사진은 5MB 이하만 올릴 수 있습니다."
private const val PROFILE_IMAGE_UNSUPPORTED_TYPE_MESSAGE = "JPEG, PNG, WebP 형식의 사진만 올릴 수 있습니다."
private const val PROFILE_IMAGE_INVALID_MESSAGE = "사진 파일을 읽을 수 없습니다. 다른 사진을 선택해 주세요."
private const val PROFILE_IMAGE_STORAGE_UNAVAILABLE_MESSAGE = "사진을 저장할 수 없습니다. 잠시 후 다시 시도해 주세요."

/** The upload exceeds [ProfileImageProcessor.MAX_UPLOAD_BYTES] (HDN-140). */
class ProfileImageTooLargeException : RuntimeException(PROFILE_IMAGE_TOO_LARGE_MESSAGE)

/** The upload's magic bytes are not JPEG, PNG or WebP, whatever its file name or content type claims (HDN-140). */
class ProfileImageUnsupportedTypeException : RuntimeException(PROFILE_IMAGE_UNSUPPORTED_TYPE_MESSAGE)

/** The upload looks like a supported format but cannot be decoded cleanly: corrupt, truncated or oversized (HDN-140). */
class ProfileImageInvalidException(
    cause: Throwable? = null,
) : RuntimeException(PROFILE_IMAGE_INVALID_MESSAGE, cause)

/** Object storage could not take or hand out the picture; nothing in the database changed (HDN-140). */
class ProfileImageStorageUnavailableException(
    cause: Throwable,
) : RuntimeException(PROFILE_IMAGE_STORAGE_UNAVAILABLE_MESSAGE, cause)
