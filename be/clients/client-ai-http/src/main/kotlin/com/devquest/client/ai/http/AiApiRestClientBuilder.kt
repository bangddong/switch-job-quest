package com.devquest.client.ai.http

import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/**
 * `AiHttpClientConfig`(core-api)의 `@Bean` 메서드가 실제로 수행하는 빌드 로직. Spring 컨테이너 없이도(플레인
 * 함수 호출) 테스트에서 재사용할 수 있도록 톱레벨 함수로 분리했다 — 타임아웃 동작 검증
 * (`AiHttpAdapterTimeoutAndAcceptHeaderTest`)이 실제 `JdkClientHttpRequestFactory` 설정을 그대로
 * 써야 하기 때문(예: `MockRestServiceServer`는 요청 팩토리 자체를 교체해 타임아웃 설정을 우회한다).
 *
 * Stage B-2a — `core-api`의 `com.devquest.core.api.config.AiHttpClientConfig.kt`에서 이 모듈로 이동.
 * HTTP 어댑터(및 그 테스트)의 소비처가 이 모듈로 옮겨오면서, 이 함수도 같은 모듈에 두는 것이 맞다
 * (원래도 `core-api`와 무관하게 Spring 의존 없이 순수 `RestClient` 빌더였음 — 동작 변경 없음).
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
