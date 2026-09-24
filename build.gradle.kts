import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.2.21"
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
    id("dev.zacsweers.redacted") version "1.15.1"
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
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.apache.commons:commons-csv:1.12.0")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

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
    testImplementation("org.springframework.security:spring-security-test")
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

    // Firebase Admin SDK for push notifications
    implementation("com.google.firebase:firebase-admin:9.4.3")
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
        // Integration-tagged tests need the Docker stack (Postgres/Keycloak); excluded by
        // default. Run them with: ./gradlew test -PincludeIntegration
        if (!project.hasProperty("includeIntegration")) {
            excludeTags("integration")
        }
    }
}

val openApiPort = 8089
val openApiDbPort = providers.gradleProperty("openApiDbPort")
val openApiEnvFile = rootProject.file(providers.gradleProperty("openApiEnvFile").orElse(".env").get())
val opsDir = rootProject.file(providers.gradleProperty("opsDir").orElse("../hanmaum-dn-ops").get())

openApi {
    apiDocsUrl.set("http://127.0.0.1:$openApiPort/v3/api-docs.yaml")
    outputDir.set(layout.buildDirectory.dir("openapi"))
    outputFileName.set("openapi.yaml")
    waitTimeInSeconds.set(60)
    customBootRun {
        val envVars =
            if (openApiEnvFile.isFile) {
                openApiEnvFile
                    .readLines()
                    .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
                    .associate { line ->
                        val idx = line.indexOf("=")
                        line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                    }
            } else {
                emptyMap()
            }
        environment.set(envVars + ("DB_HANMAUM_DN_PORT" to openApiDbPort.getOrElse("5433")))
        systemProperties.set(
            mapOf(
                "spring.profiles.active" to "dev",
                "server.port" to openApiPort.toString(),
                "spring.datasource.url" to
                    "jdbc:postgresql://127.0.0.1:${openApiDbPort.getOrElse("5433")}/${envVars["DB_HANMAUM_DN_NAME"] ?: ""}",
            ),
        )
    }
}

val validateOpenApiSync =
    tasks.register("validateOpenApiSync") {
        doLast {
            check(opsDir.resolve(".git").exists() && opsDir.resolve("api/openapi.yaml").isFile) {
                "Ops directory must be an existing hanmaum-dn-ops Git checkout with api/openapi.yaml: $opsDir. " +
                    "Pass -PopsDir=<path> when running from a worktree."
            }
        }
    }

tasks.named("forkedSpringBootRun") {
    mustRunAfter(validateOpenApiSync)
    doFirst {
        val dbPort = openApiDbPort.orNull?.toIntOrNull()
        check(dbPort != null && dbPort in 1..65535 && dbPort != 5433) {
            "OpenAPI generation requires -PopenApiDbPort=<disposable PostgreSQL port> (not shared dev port 5433)."
        }
        check(openApiEnvFile.isFile) {
            "OpenAPI environment file not found: $openApiEnvFile. Pass -PopenApiEnvFile=<path>."
        }
        try {
            ServerSocket().use { it.bind(InetSocketAddress("127.0.0.1", openApiPort)) }
        } catch (e: BindException) {
            throw GradleException("OpenAPI port $openApiPort is already in use; stop that process before generating the spec.", e)
        }
    }
}

tasks.named("generateOpenApiDocs") {
    outputs.upToDateWhen { false }
    doLast {
        val spec =
            layout.buildDirectory
                .file("openapi/openapi.yaml")
                .get()
                .asFile
        val generatedUrl = "url: http://127.0.0.1:$openApiPort"
        val contents = spec.readText()
        check(contents.contains(generatedUrl)) {
            "Generated OpenAPI spec has an unexpected server URL; refusing to sync: $spec"
        }
        spec.writeText(contents.replaceFirst(generatedUrl, "url: http://localhost:8080"))
    }
}

tasks.register<Copy>("syncOpenApiToOps") {
    dependsOn(validateOpenApiSync, "generateOpenApiDocs")
    from(layout.buildDirectory.file("openapi/openapi.yaml"))
    into(opsDir.resolve("api"))
}
