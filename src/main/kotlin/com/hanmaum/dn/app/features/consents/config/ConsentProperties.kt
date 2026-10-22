package com.hanmaum.dn.app.features.consents.config

import com.hanmaum.dn.app.common.domainvalue.ConsentPurpose
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * The policy version in force per purpose, and which purposes are enforced yet.
 *
 * Bump a version when its text changes in a way members must agree to again: every standing
 * grant of the older version then reads as OUTDATED and the app asks again.
 *
 * [enforced] exists for the rollout. A purpose not listed is recorded when a client sends it
 * but not required, so app builds that predate the consent screen keep working. Add the
 * purpose once a build that sends it is out.
 */
@ConfigurationProperties("app.consents")
data class ConsentProperties(
    val versions: Map<ConsentPurpose, String> = emptyMap(),
    val enforced: Set<ConsentPurpose> = emptySet(),
) {
    /** A purpose without a configured version is on [DEFAULT_VERSION], so a new one needs no config. */
    fun versionOf(purpose: ConsentPurpose): String = versions[purpose]?.takeIf { it.isNotBlank() } ?: DEFAULT_VERSION

    fun isEnforced(purpose: ConsentPurpose): Boolean = purpose in enforced

    companion object {
        const val DEFAULT_VERSION = "v1"
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConsentProperties::class)
class ConsentConfiguration
