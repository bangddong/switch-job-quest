# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY gradle gradle
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties ./
COPY core core
COPY storage storage
COPY clients clients
COPY support support
COPY tests tests

RUN chmod +x gradlew && \
    ./gradlew :core:core-api:bootJar --no-daemon -x test

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/core/core-api/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
