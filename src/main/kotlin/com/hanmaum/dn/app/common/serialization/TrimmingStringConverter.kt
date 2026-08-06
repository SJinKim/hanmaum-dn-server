package com.hanmaum.dn.app.common.serialization

import tools.jackson.databind.util.StdConverter

/**
 * Trims leading/trailing whitespace from an incoming string during JSON deserialization —
 * i.e. before bean validation runs. Applied to identifier fields (e.g. email) so a stray
 * space from a mobile keyboard or copy-paste cannot produce a mismatched Keycloak username
 * or a spurious `@Email` validation failure. Never applied to passwords, where surrounding
 * whitespace may be intentional. Null-safe so it can also decorate optional fields.
 */
class TrimmingStringConverter : StdConverter<String?, String?>() {
    override fun convert(value: String?): String? = value?.trim()
}
