plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.2.21"
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
}

group = "com.hanmaum.dn"
version = "0.0.1-SNAPSHOT"
description = "hanmaum D+N Fullstack App"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot 4 Standard Dependencies
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // DB
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")

    // Auth & Utils
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    // Liest .env Dateien und macht sie als Spring Properties verfügbar
    implementation("me.paulschwarz:spring-dotenv:4.0.0")

    // Spring Boot Support für Testcontainers
    testImplementation("org.springframework.boot:spring-boot-testcontainers")

    // Restliche
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // --- TEST SETUP (Spring Boot 4 & Testcontainers 2) ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    // Test Container libs
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")

    // Enthält UserRepresentation, CredentialRepresentation und den Client
    implementation("org.keycloak:keycloak-admin-client:24.0.1")

    // Falls du Spring Boot 3 nutzt (Jakarta EE), brauchst du oft auch das RESTEasy Backend:
    implementation("org.jboss.resteasy:resteasy-client:6.2.7.Final")
    implementation("org.jboss.resteasy:resteasy-jackson2-provider:6.2.7.Final")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

ktlint {
    outputToConsole.set(true)
    ignoreFailures.set(false)

    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
    }
}

tasks.withType<Test> {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

openApi {
    apiDocsUrl.set("http://localhost:8080/v3/api-docs.yaml")
    outputDir.set(layout.buildDirectory.dir("openapi"))
    outputFileName.set("openapi.yaml")
    waitTimeInSeconds.set(40)
}

// Run after generateOpenApiDocs to push the spec into the ops repo.
// Requires Docker services (Postgres + Keycloak) to be up.
tasks.register<Copy>("syncOpenApiToOps") {
    dependsOn("generateOpenApiDocs")
    from(layout.buildDirectory.file("openapi/openapi.yaml"))
    into("../hanmaum-dn-ops/api")
}
