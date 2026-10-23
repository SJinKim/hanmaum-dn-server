package com.hanmaum.dn.app.features.consents.config

import com.hanmaum.dn.app.common.domainvalue.ConsentPurpose
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * The policy version in force per purpose.
 *
 * Bump a version when its text changes in a way members must agree to again: every standing
 * grant of the older version then reads as OUTDATED and the app asks again.
 */
@ConfigurationProperties("app.consents")
data class ConsentProperties(
    val versions: Map<ConsentPurpose, String> = emptyMap(),
) {
    /** A purpose without a configured version is on [DEFAULT_VERSION], so a new one needs no config. */
    fun versionOf(purpose: ConsentPurpose): String = versions[purpose]?.takeIf { it.isNotBlank() } ?: DEFAULT_VERSION

    companion object {
        const val DEFAULT_VERSION = "v1"
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConsentProperties::class)
class ConsentConfiguration
