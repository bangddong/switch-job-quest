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
#   prod GC 로그 실측: 사용 42MB / 커밋 117MB, 상한 179MB 대비 여유 충분 — 힙은 무죄, 건드리지 말 것
# - Metaspace: 160MB 상한. 2026-07-14 128MB로 축소했다가 prod에서 Metaspace OOME로 다운(배포 34시간 후).
#   GC 로그가 전부 "Pause Full (Metadata GC Threshold / Clear Soft References)"였고 42M->42M(회수 안 됨)로
#   2초 풀 GC가 무한 반복되는 데스 스파이럴 끝에 OutOfMemoryError: Metaspace 발생. 160m으로 복구.
#   (34시간 만에 발생한 것으로 보아 메타스페이스가 서서히 증가하는 것으로 추정 — 원인 미검증, 별도 조사 필요)
# - Reserved Code Cache: 96MB 상한 (JIT 컴파일 코드 캐시 — native 메모리 상한 고정으로 억제)
# - 스레드 스택: 512k — 플랫폼(carrier) 스레드·GC 스레드 등 JVM 내부 스레드 스택 절감용.
#   spring.threads.virtual.enabled=true로 요청 처리는 가상 스레드(힙 스택)를 사용하므로 영향 없음
# - GC 로그(-Xlog:gc)는 배포 후 실제 사용 중인 GC 종류 확정용. 저트래픽 환경이라 로그량 미미.
#   Metaspace OOME 진단에 이 로그가 결정적이었으므로 절대 제거하지 말 것
# - 나머지는 스레드 오버헤드, 기타 네이티브 메모리용으로 여유 확보
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=35.0", \
  "-XX:MaxMetaspaceSize=160m", \
  "-XX:ReservedCodeCacheSize=96m", \
  "-Xss512k", \
  "-Xlog:gc::time,level,tags", \
  "-jar", "app.jar"]
