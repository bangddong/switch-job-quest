package com.devquest.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import com.github.loki4j.logback.Loki4jAppender
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.springframework.boot.logging.LoggingInitializationContext
import org.springframework.boot.logging.logback.LogbackLoggingSystem
import org.springframework.mock.env.MockEnvironment

/**
 * 2026-07-27 EKS 배포 인시던트 재현/회귀 테스트.
 *
 * [LogbackLoggingSystem.initialize]는 Spring Boot가 실제 부팅 시 호출하는 바로 그 진입점이다.
 * 내부에서 Joran으로 `logback-spring.xml`을 로딩한 뒤 StatusManager에 ERROR 레벨 상태가
 * 하나라도 있으면 `IllegalStateException`을 던져 애플리케이션 부팅 자체를 중단시킨다.
 * 이 테스트는 그 실제 코드 경로를 그대로 태워서(우회 없이) "GRAFANA_LOKI_URL 미설정 시
 * prod 프로파일 앱이 부팅에 실패한다"는 인시던트를 정확히 재현한다.
 *
 * 순수 logback [ch.qos.logback.classic.joran.JoranConfigurator]나 Spring Boot의
 * `SpringBootJoranConfigurator`(패키지 프라이빗이라 외부에서 직접 접근 불가)를 손수 조립하는
 * 대신 공개 API인 [LogbackLoggingSystem]을 쓴다 — 실제 부팅 경로와 동일한 해석기·후처리
 * (ERROR 상태 검사 → 예외 변환)를 그대로 사용하기 위함이다.
 */
class LogbackLokiConfigTest {

    private val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext

    @AfterEach
    fun resetLoggingContext() {
        // 각 테스트가 전역 SLF4J LoggerContext(JVM 싱글턴)에 appender를 붙인다.
        // LOKI appender는 백그라운드 배치 전송 스레드를 띄우므로, 다음 테스트·같은 JVM에서
        // 도는 다른 코드에 영향이 남지 않도록 매 테스트 후 초기 상태로 되돌린다.
        loggerContext.reset()
    }

    private fun initialize(
        activeProfile: String,
        lokiUrl: String?,
        extraProperties: Map<String, String> = emptyMap(),
    ) {
        val environment = MockEnvironment()
        environment.setActiveProfiles(activeProfile)
        if (lokiUrl != null) {
            environment.setProperty("GRAFANA_LOKI_URL", lokiUrl)
        }
        extraProperties.forEach { (key, value) -> environment.setProperty(key, value) }

        val loggingSystem = LogbackLoggingSystem(javaClass.classLoader)
        loggingSystem.beforeInitialize()
        loggingSystem.initialize(LoggingInitializationContext(environment), "classpath:logback-spring.xml", null)
    }

    private fun rootAppenderNames(): List<String> =
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)
            .iteratorForAppenders()
            .asSequence()
            .map { it.name }
            .toList()

    @Test
    fun `GRAFANA_LOKI_URL이 없으면 prod 프로파일에서도 예외 없이 STDOUT만으로 부팅된다`() {
        initialize(activeProfile = "prod", lokiUrl = null)

        assertThat(rootAppenderNames()).containsExactly("STDOUT")
    }

    @Test
    fun `GRAFANA_LOKI_URL이 있으면 LOKI appender가 root logger에 정상적으로 붙는다`() {
        initialize(activeProfile = "prod", lokiUrl = "http://loki.example.com:3100/loki/api/v1/push")

        assertThat(rootAppenderNames()).containsExactlyInAnyOrder("STDOUT", "LOKI")
    }

    /**
     * 가짜 Loki(로컬 HTTP 서버)를 띄우고 로그 한 줄을 흘려, 실제로 전송된 요청 본문의 **스트림 라벨**을 돌려준다.
     * Loki4j 의 라벨 설정은 외부에서 읽을 방법이 없어서(필드 private) 전송 결과로 검증한다.
     */
    private fun captureLokiPush(extraProperties: Map<String, String>): String {  // 반환: 스트림 라벨 객체 내부 문자열
        val bodies = CopyOnWriteArrayList<String>()
        val received = CountDownLatch(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/loki/api/v1/push") { exchange ->
            bodies.add(String(exchange.requestBody.readAllBytes(), Charsets.UTF_8))
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            received.countDown()
        }
        server.start()
        try {
            initialize(
                activeProfile = "prod",
                lokiUrl = "http://127.0.0.1:${server.address.port}/loki/api/v1/push",
                extraProperties = extraProperties,
            )
            LoggerFactory.getLogger("loki-label-probe").info("label probe")
            // stop() 이 남은 배치를 비운다(drainOnStop 기본 true).
            val loki = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("LOKI") as Loki4jAppender
            loki.stop()
            assertThat(received.await(10, TimeUnit.SECONDS)).describedAs("Loki push 수신").isTrue()
            // {"streams":[{"stream":{<라벨>},"values":[...]}]} 에서 라벨 객체만 꺼낸다(메시지 본문의 "level" 과 섞이지 않게).
            return bodies.first().substringAfter("\"stream\":{").substringBefore("}")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `Loki 라벨은 app 과 platform 과 level 이다 - daily-api 서비스 이름과 EKS 플랫폼`() {
        val labels = captureLokiPush(
            mapOf(
                "devquest.observability.service-name" to "devquest-daily-api",
                "DEVQUEST_PLATFORM" to "eks",
            ),
        )

        assertThat(labels).contains("\"app\":\"devquest-daily-api\"")
        assertThat(labels).contains("\"platform\":\"eks\"")
        assertThat(labels).contains("\"level\":\"INFO\"")
    }

    @Test
    fun `서비스 이름과 플랫폼이 없으면 기본값 devquest-api 와 unknown 이다 - 기존 대시보드 쿼리 유지`() {
        val labels = captureLokiPush(emptyMap())

        assertThat(labels).contains("\"app\":\"devquest-api\"")
        assertThat(labels).contains("\"platform\":\"unknown\"")
    }
}
