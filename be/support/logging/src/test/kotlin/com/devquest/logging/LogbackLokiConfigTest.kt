package com.devquest.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
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

    private fun initialize(activeProfile: String, lokiUrl: String?) {
        val environment = MockEnvironment()
        environment.setActiveProfiles(activeProfile)
        if (lokiUrl != null) {
            environment.setProperty("GRAFANA_LOKI_URL", lokiUrl)
        }

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
}
