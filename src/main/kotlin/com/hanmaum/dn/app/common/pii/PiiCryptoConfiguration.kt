package com.hanmaum.dn.app.common.pii

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import java.net.URI

@Configuration
@EnableConfigurationProperties(PiiProperties::class)
@Import(PiiCryptoJpaBootstrapDependency::class)
class PiiCryptoConfiguration(
    private val properties: PiiProperties,
    private val environment: Environment,
    @Value("\${spring.datasource.url:}") private val datasourceUrl: String,
) {
    private val log = LoggerFactory.getLogger(PiiCryptoConfiguration::class.java)

    @PostConstruct
    fun initialize() {
        val plaintextWriteEnabled =
            PiiLocalPlaintextPolicy.validateAndResolve(
                properties = properties,
                activeProfiles = environment.activeProfiles.toSet(),
                datasourceUrl = datasourceUrl,
            )
        val keyring = PiiKeyringLoader.load(properties)
        PiiCryptoContext.initialize(
            PiiCryptoService(
                keyring = keyring,
                legacyPlaintextReadEnabled = properties.legacyPlaintextReadEnabled,
                plaintextWriteEnabled = plaintextWriteEnabled,
            ),
        )

        if (plaintextWriteEnabled) {
            log.warn(
                "PII crypto initialized keyId={} mode=LOCAL_PLAINTEXT; database PII is readable and must never be shared",
                keyring.activeKeyId,
            )
        } else if (properties.strict) {
            log.info("PII crypto initialized keyId={} mode=strict", keyring.activeKeyId)
        } else {
            log.warn("PII crypto initialized keyId={} mode=development", keyring.activeKeyId)
        }
    }
}

internal object PiiLocalPlaintextPolicy {
    private val allowedProfiles = setOf("dev", "local")
    private val forbiddenProfiles = setOf("prod", "production", "staging", "stage", "st")
    private val allowedHosts =
        setOf(
            "localhost",
            "127.0.0.1",
            "::1",
            "[::1]",
            "hanmaumapp-db",
            "test-db",
            "host.docker.internal",
        )

    fun validateAndResolve(
        properties: PiiProperties,
        activeProfiles: Set<String>,
        datasourceUrl: String,
    ): Boolean {
        if (!properties.localPlaintextEnabled) {
            return false
        }

        val normalizedProfiles = activeProfiles.map { it.lowercase() }.toSet()
        check(!properties.strict) {
            "Local plaintext PII cannot be enabled while app.pii.strict=true."
        }
        check(normalizedProfiles.none(forbiddenProfiles::contains)) {
            "Local plaintext PII is forbidden for production and staging profiles."
        }
        check(normalizedProfiles.any(allowedProfiles::contains)) {
            "Local plaintext PII requires an active dev or local Spring profile."
        }

        val host = datasourceHost(datasourceUrl)
        check(host != null && host.lowercase() in allowedHosts) {
            "Local plaintext PII requires a local PostgreSQL host; configured host was '${host ?: "<invalid>"}'."
        }
        return true
    }

    private fun datasourceHost(datasourceUrl: String): String? =
        runCatching {
            URI.create(datasourceUrl.removePrefix("jdbc:")).host
        }.getOrNull()
}

/**
 * JPA evaluates enum converters while building Hibernate metadata. Make that
 * bootstrap explicitly depend on the keyring initialization instead of relying
 * on incidental Spring bean ordering.
 */
class PiiCryptoJpaBootstrapDependency : BeanFactoryPostProcessor {
    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        if (!beanFactory.containsBeanDefinition(ENTITY_MANAGER_FACTORY_BEAN)) {
            return
        }

        val piiConfigurationBean =
            beanFactory
                .getBeanNamesForType(PiiCryptoConfiguration::class.java, false, false)
                .single()
        val definition = beanFactory.getBeanDefinition(ENTITY_MANAGER_FACTORY_BEAN)
        val dependencies =
            definition.dependsOn
                .orEmpty()
                .filterNot { it == piiConfigurationBean }
                .plus(piiConfigurationBean)
                .toTypedArray()
        definition.setDependsOn(*dependencies)
    }

    private companion object {
        const val ENTITY_MANAGER_FACTORY_BEAN = "entityManagerFactory"
    }
}
