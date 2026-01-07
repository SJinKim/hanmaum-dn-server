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
# Hier reicht uns das JRE (Java Runtime), das ist viel kleiner
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Wir holen uns nur die fertige JAR aus der "builder"-Stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Port 8080 ist Standard bei Spring Boot
EXPOSE 8080

# Der Startbefehl
ENTRYPOINT ["java", "-jar", "app.jar"]