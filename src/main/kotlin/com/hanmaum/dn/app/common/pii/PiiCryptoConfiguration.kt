package com.hanmaum.dn.app.common.pii

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@EnableConfigurationProperties(PiiProperties::class)
@Import(PiiCryptoJpaBootstrapDependency::class)
class PiiCryptoConfiguration(
    private val properties: PiiProperties,
) {
    private val log = LoggerFactory.getLogger(PiiCryptoConfiguration::class.java)

    @PostConstruct
    fun initialize() {
        val keyring = PiiKeyringLoader.load(properties)
        PiiCryptoContext.initialize(
            PiiCryptoService(
                keyring = keyring,
                legacyPlaintextReadEnabled = properties.legacyPlaintextReadEnabled,
            ),
        )

        if (properties.strict) {
            log.info("PII crypto initialized keyId={} mode=strict", keyring.activeKeyId)
        } else {
            log.warn("PII crypto initialized keyId={} mode=development", keyring.activeKeyId)
        }
    }
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
