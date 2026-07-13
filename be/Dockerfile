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

# 컨테이너 메모리 예산: 512MB (Fly.io shared-cpu-1x) — 근사치, 배포 후 실측 검증 필요
# - 힙: MaxRAMPercentage=35.0 → 최대 약 179MB (RSS creep로 인한 OOM 재발 방지 위해 50%=256MB에서 축소)
# - Metaspace: 128MB 상한 (클래스 메타데이터, 무제한 성장 방지, 기존 160m 과다 상한 축소)
# - Reserved Code Cache: 96MB 상한 (JIT 컴파일 코드 캐시 — native 메모리 상한 고정으로 억제)
# - 스레드 스택: 512k — 플랫폼(carrier) 스레드·GC 스레드 등 JVM 내부 스레드 스택 절감용.
#   spring.threads.virtual.enabled=true로 요청 처리는 가상 스레드(힙 스택)를 사용하므로 영향 없음
# - GC 로그(-Xlog:gc)는 배포 후 실제 사용 중인 GC 종류 확정용. 저트래픽 환경이라 로그량 미미
# - 나머지는 스레드 오버헤드, 기타 네이티브 메모리용으로 여유 확보
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=35.0", \
  "-XX:MaxMetaspaceSize=128m", \
  "-XX:ReservedCodeCacheSize=96m", \
  "-Xss512k", \
  "-Xlog:gc::time,level,tags", \
  "-jar", "app.jar"]
