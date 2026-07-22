package com.devquest.core.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * ai-api 호출용 공용 `RestClient` 빈. `devquest.ai.transport=http`일 때만 등록된다 — inprocess(기본값)
 * 에서는 이 클래스의 `@Bean` 메서드가 아예 호출되지 않아 `AiTransportConfig`의 HTTP 어댑터 빈들도
 * (같은 조건이라) 등록되지 않는다. Phase 1 Task 1.4a.
 *
 * ⚠️ **`ObjectMapper` 빈을 별도로 만들지 않는다 (Task 1.4b 정정, QA MEDIUM #1 후속)**. Task 1.4a는
 * `jacksonObjectMapper()`(Jackson 2)로 전용 빈을 만들어 `BaseAiHttpAdapter`가 그걸로 직접
 * (역)직렬화하게 했지만, ai-api 서버(별도 프로세스, Boot 4 기본값)는 Jackson 3
 * (`tools.jackson.databind.json.JsonMapper`)로 (역)직렬화한다 — core가 J2로 쓰고 ai-api가 J3로 읽는
 * 비대칭이었다("프레임워크 버전과 무관하게"는 틀린 목표였고, 옳은 목표는 ai-api의 실제 선택인 J3와
 * **일치**시키는 것). core-api Spring 컨텍스트는 `spring-boot-starter-jackson`이 자동구성하는 J3
 * `ObjectMapper`(`tools.jackson.databind.ObjectMapper`) 빈을 **이미** 갖고 있고(`AiCheckService`·
 * `CompanyService`가 이미 이 타입을 생성자로 주입받아 씀), Kotlin 모듈(널러블·기본값 필드)도 등록돼
 * 있음이 `AiHttpJacksonV3ContextTest`로 실측 확인됐다. 그래서 이 클래스는 더 이상 `ObjectMapper` 빈을
 * 만들지 않는다 — [com.devquest.core.api.config.AiTransportConfig]의 각 `@Bean` 메서드가 파라미터로
 * 요구하는 `tools.jackson.databind.ObjectMapper`는 Boot가 자동구성한 그 빈이 그대로 주입된다.
 *
 * ⚠️ **타임아웃 (계획 문서 함정 (a))**: `RestClient`는 기본 요청 팩토리 설정 시 커넥트/리드 타임아웃이
 * 사실상 무제한이다. AI 평가는 수 초~수십 초가 걸리므로 명시적으로 설정하지 않으면 커넥션·스레드가
 * 영구 점유될 위험이 있다.
 * - `connect-timeout-ms` 기본 3000ms: ai-api는 같은 내부망(로컬 개발 기준 `localhost:8081`, Phase 3
 *   EKS에서는 클러스터 내부 서비스)이라 TCP 연결 자체는 즉시 성립해야 한다. 3초 안에도 안 붙으면
 *   네트워크·기동 문제로 보고 재시도(ai-api 쪽 `AiCallExecutor`) 없이 바로 실패시킨다.
 * - `read-timeout-ms` 기본 **150000ms(150초, Task 1.4b 정정 — 기존 90000ms는 안전 마진이 0이었음)**.
 *   계산 근거: ai-api 내부 `AiCallExecutor`(`devquest.ai.max-retry`=3)가 **같은 HTTP 요청·응답
 *   사이클 안에서** 백오프 없이 최대 3회 순차 재시도한다. Anthropic 호출 1회 상한을 기존 코드 주석이
 *   가정한 "수십 초"(30000ms)로 두면 3회 순차 합은 30000×3=90000ms — 이게 기존 기본값과 **정확히
 *   일치**했다. 즉 core가 ai-api보다 먼저 끊길 여지가 전혀 없는 게 아니라, 단 1ms의 지연(직렬화·역직렬화·
 *   TCP 재전송·GC 정지 등)만 더해져도 core가 먼저 타임아웃되고 ai-api는 응답을 마저 만드는 채로 계속
 *   돌며 LLM 비용을 태운다. → 같은 90000ms 위에 안전 마진 60000ms(60초, 요청/응답 직렬화·네트워크
 *   재전송·GC·ai-api 자체 디스패치 오버헤드 흡수용)를 더해 **150000ms**로 상향한다.
 */
@Configuration
class AiHttpClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun aiApiRestClient(
        @Value("\${devquest.ai.http.base-url:http://localhost:8081}") baseUrl: String,
        @Value("\${devquest.ai.http.connect-timeout-ms:3000}") connectTimeoutMs: Long,
        @Value("\${devquest.ai.http.read-timeout-ms:150000}") readTimeoutMs: Long,
    ): RestClient = buildAiApiRestClient(baseUrl, connectTimeoutMs, readTimeoutMs)
}

/**
 * `AiHttpClientConfig`의 `@Bean` 메서드가 실제로 수행하는 빌드 로직. Spring 컨테이너 없이도(플레인
 * 함수 호출) 테스트에서 재사용할 수 있도록 톱레벨 함수로 분리했다 — 타임아웃 동작 검증
 * (`AiHttpAdapterTimeoutAndAcceptHeaderTest`)이 실제 `JdkClientHttpRequestFactory` 설정을 그대로
 * 써야 하기 때문(예: `MockRestServiceServer`는 요청 팩토리 자체를 교체해 타임아웃 설정을 우회한다).
 */
fun buildAiApiRestClient(baseUrl: String, connectTimeoutMs: Long, readTimeoutMs: Long): RestClient {
    val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
        .build()
    val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
        setReadTimeout(Duration.ofMillis(readTimeoutMs))
    }
    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .build()
}
