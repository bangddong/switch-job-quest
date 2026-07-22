package com.devquest.core.api.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
 * ⚠️ **타임아웃 (계획 문서 함정 (a))**: `RestClient`는 기본 요청 팩토리 설정 시 커넥트/리드 타임아웃이
 * 사실상 무제한이다. AI 평가는 수 초~수십 초가 걸리므로 명시적으로 설정하지 않으면 커넥션·스레드가
 * 영구 점유될 위험이 있다.
 * - `connect-timeout-ms` 기본 3000ms: ai-api는 같은 내부망(로컬 개발 기준 `localhost:8081`, Phase 3
 *   EKS에서는 클러스터 내부 서비스)이라 TCP 연결 자체는 즉시 성립해야 한다. 3초 안에도 안 붙으면
 *   네트워크·기동 문제로 보고 재시도(ai-api 쪽 `AiCallExecutor`) 없이 바로 실패시킨다.
 * - `read-timeout-ms` 기본 90000ms(90초): ai-api 내부의 `AiCallExecutor`가 `max-retry`=3으로 **같은
 *   HTTP 요청·응답 사이클 안에서** 최대 3회 순차 재시도한다(Anthropic 호출 1회가 `max-tokens`=8000
 *   기준 길면 수십 초 걸릴 수 있음). 3회가 겹치면 단일 HTTP 응답이 오는 데 90초 가까이 걸릴 수 있어,
 *   이보다 짧게 잡으면 ai-api가 실제로 성공 처리 중인 정상 요청이 core 쪽에서 먼저 잘려나간다.
 */
@Configuration
class AiHttpClientConfig {

    /**
     * Boot 4.x는 Jackson 3(`tools.jackson.databind.json.JsonMapper`)을 기본 자동구성하고
     * `com.fasterxml.jackson.databind.ObjectMapper`(Jackson 2) 빈은 등록하지 않는다. `BaseAiHttpAdapter`가
     * 요청/응답을 항상 `String`으로 다루고 이 빈으로 직접 (역)직렬화하므로, 프레임워크가 내부적으로
     * 어떤 Jackson 메이저 버전을 쓰는지와 완전히 무관하게 동작한다. `jacksonObjectMapper()`가 Kotlin
     * 모듈을 등록해 data class의 널러블/기본값 필드를 올바르게 다룬다.
     */
    @Bean
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun aiApiObjectMapper(): ObjectMapper = jacksonObjectMapper()

    @Bean
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun aiApiRestClient(
        @Value("\${devquest.ai.http.base-url:http://localhost:8081}") baseUrl: String,
        @Value("\${devquest.ai.http.connect-timeout-ms:3000}") connectTimeoutMs: Long,
        @Value("\${devquest.ai.http.read-timeout-ms:90000}") readTimeoutMs: Long,
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
