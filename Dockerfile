# -----------------------------------------------------------------------------
# STAGE 1: Build (Baut die JAR-Datei)
# -----------------------------------------------------------------------------
# Wir nutzen ein Image mit Java 21 JDK, um den Code zu kompilieren
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# 1. Gradle-Dateien kopieren (Caching-Optimierung)
# So muss Docker nicht bei jeder Code-Änderung alle Dependencies neu laden
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Mache den Gradle Wrapper ausführbar (wichtig bei Windows -> Linux)
RUN chmod +x ./gradlew

# (Optional) Dependencies vorladen
# Wenn das fehlschlägt, macht der nächste Schritt den Rest, daher "|| return 0"
RUN ./gradlew dependencies --no-daemon || return 0

# 2. Quellcode kopieren und bauen
COPY src src

# Wir bauen die Jar und überspringen Tests (spart Zeit im Container)
RUN ./gradlew bootJar -x test --no-daemon

# -----------------------------------------------------------------------------
# STAGE 2: Run (Führt die App aus)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl \
    && adduser -D -u 1001 appuser \
    && mkdir -p /app /tmp/app \
    && chown -R appuser:appuser /app /tmp/app

ENV JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=/tmp/app"

USER appuser
WORKDIR /app

COPY --from=builder --chown=appuser:appuser /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]