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

# 컨테이너 메모리 예산: 512MB (Fly.io shared-cpu-1x)
# - 힙: MaxRAMPercentage=50.0 → 최대 256MB (기존 75%=384MB는 비힙 공간 부족으로 구조적 OOM 유발)
# - Metaspace: 160MB 상한 (클래스 메타데이터, 무제한 성장 방지)
# - 스레드 스택: 512k (기본 1MB 대비 절반, Tomcat max threads=25와 정합되어 총 스택 사용량 절감)
# - 나머지(~96MB)는 코드캐시, 네이티브 메모리, 스레드 오버헤드용으로 여유 확보
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=50.0", \
  "-XX:MaxMetaspaceSize=160m", \
  "-Xss512k", \
  "-jar", "app.jar"]
