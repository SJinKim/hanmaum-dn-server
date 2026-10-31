package com.hanmaum.dn.app.features.attendance.api.v1

/** The JWT of a long-absence write has no usable subject; answered with 401, nothing of the token is kept. */
class InvalidActorSubjectException : RuntimeException("Missing actor subject")
