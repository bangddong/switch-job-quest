package com.devquest.daily.config

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.bootstrap.DefaultBootstrapContext
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor
import org.springframework.boot.logging.LoggingInitializationContext
import org.springframework.boot.logging.LoggingSystem
import org.springframework.boot.logging.logback.LogbackLoggingSystem
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.DefaultResourceLoader

/**
 * ⑥ B8-18 / QA F-1 회귀 가드 - 앱 yml 의 `devquest.observability.service-name` 이 실제 부팅 경로를 따라
 * logback `springProperty`(`lokiAppLabel`)까지 넘어가는지 본다. 틀리면 daily-api 로그가 `app=devquest-api` 로 core-api 와 섞인다.
 *
 * support:logging 의 `LogbackLokiConfigTest` 는 `MockEnvironment` 에 프로퍼티를 직접 넣어서 "앱 yml -> Environment" 구간을 보지 못한다.
 * 여기서는 Spring Boot 가 부팅 때 쓰는 [ConfigDataEnvironmentPostProcessor] 로 daily-api 의 application.yml / application-prod.yml /
 * `spring.config.import`(monitoring.yml)를 **실제 우선순위대로** 해석한 Environment 를 [LogbackLoggingSystem] 에 넘긴다.
 *
 * prod 프로필 컨텍스트 전체 기동은 Postgres 접속 정보를 요구해서 하지 않는다 - logback 설정 단계까지만 실제 경로를 탄다.
 * 🔴 `GRAFANA_LOKI_URL` 을 빈 값으로 고정한다: 개발자 셸에 값이 있어도 Loki appender 가 붙지 않아 외부 네트워크 push 가 없다.
 */
class DailyApiLokiLabelWiringTest {

    private val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext

    /** 테스트 시작 전에 Boot 가 이 JVM 의 LoggerContext 를 이미 초기화(표시)해 뒀는가. */
    private var wasMarkedInitialized = false

    @BeforeEach
    fun clearInitializedMark() {
        // 🔴 QA F-7 - LogbackLoggingSystem 은 LoggerContext 에 `LoggingSystem` 클래스명 키 객체가 있으면(= 이미 초기화) beforeInitialize/initialize 를
        // 곧바로 return 한다(Boot 4.0.3 `isAlreadyInitialized`). 같은 JVM 에서 앞서 돈 @SpringBootTest 가 그 표시를 남기므로,
        // 지우지 않으면 이 테스트의 initialize 가 no-op 이 되어 lokiAppLabel 이 null 이 된다(클래스 실행 순서 의존).
        // LoggerContext.reset() 은 objectMap·propertyMap 을 비운다 -> 표시가 사라진다.
        wasMarkedInitialized = loggerContext.getObject(LoggingSystem::class.java.name) != null
        loggerContext.reset()
    }

    @AfterEach
    fun restoreLogging() {
        // 전역 LoggerContext(JVM 싱글턴)를 건드렸으므로 초기화한 뒤 local 설정(STDOUT)으로 되돌려 같은 JVM 의 다른 테스트 로그를 살린다.
        loggerContext.reset()
        val restore = StandardEnvironment().apply { setActiveProfiles("local") }
        val loggingSystem = LogbackLoggingSystem(javaClass.classLoader)
        loggingSystem.beforeInitialize()
        loggingSystem.initialize(LoggingInitializationContext(restore), "classpath:logback-spring.xml", null)
        // initialize 는 끝에 "초기화됨" 표시를 남긴다. 시작 전에 표시가 없었다면 지워서, 뒤에 오는 @SpringBootTest 의
        // LoggingApplicationListener 초기화가 no-op 이 되지 않게 테스트 전 상태로 되돌린다.
        if (!wasMarkedInitialized) {
            loggerContext.removeObject(LoggingSystem::class.java.name)
        }
    }

    @Test
    fun `daily-api 앱 yml 의 service-name 이 logback lokiAppLabel 로 넘어간다`() {
        val environment = StandardEnvironment()
        environment.propertySources.addFirst(
            MapPropertySource("test-overrides", mapOf("GRAFANA_LOKI_URL" to "")),
        )
        ConfigDataEnvironmentPostProcessor.applyTo(environment, DefaultResourceLoader(), DefaultBootstrapContext(), "prod")
        assertThat(environment.activeProfiles).contains("prod")

        val loggingSystem = LogbackLoggingSystem(javaClass.classLoader)
        loggingSystem.beforeInitialize()
        loggingSystem.initialize(LoggingInitializationContext(environment), "classpath:logback-spring.xml", null)

        assertThat(loggerContext.getProperty("lokiAppLabel")).isEqualTo("devquest-daily-api")
        // Loki appender 가 없다 = 외부 push 없음.
        assertThat(loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("LOKI")).isNull()
    }
}
