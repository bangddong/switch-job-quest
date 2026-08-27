package com.devquest.daily.config

import com.devquest.client.ai.http.TechInterviewHttpAdapter
import com.devquest.client.ai.http.buildAiApiRestClient
import com.devquest.core.domain.port.TechInterviewPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper

/**
 * daily-api의 AI 전송 배선 — **무조건 등록** (함정 ③).
 *
 * core-api의 `AiHttpClientConfig`/`AiTransportConfig`는 `@ConditionalOnProperty(devquest.ai.transport
 * = http)` 게이트를 두는데, core-api는 게이트가 닫혀도 `client-ai`의 `@Component` 인프로세스
 * 평가자가 `TechInterviewPort`의 폴백 구현으로 남아 있어 컨텍스트가 깨지지 않는다. daily-api는
 * `client-ai`를 의존하지 않으므로(client-ai-http만 의존) 그 폴백이 없다 — 조건부로 게이트를 걸면
 * `TechInterviewPort` 구현체가 0개가 되어 `DailyQuestionContentService`(daily-core) 주입이
 * `NoSuchBeanDefinitionException`으로 실패한다(실측: `@ConditionalOnProperty` 없이 이 클래스를
 * 처음 만들었을 때 `DailyApiContextLoadTest`가 이 예외로 RED였다).
 *
 * `ObjectMapper`는 Jackson 3(`tools.jackson.databind.ObjectMapper`)이며 `spring-boot-starter-jackson`
 * 자동구성 빈을 그대로 주입받는다 — core-api의 `AiHttpClientConfig` KDoc과 동일 근거로 별도 빈을
 * 만들지 않는다(ai-api 서버도 Jackson 3로 (역)직렬화하므로 형식이 일치해야 한다).
 */
@Configuration
class DailyAiConfig {

    @Bean
    fun aiApiRestClient(
        @Value("\${devquest.ai.http.base-url:http://localhost:8081}") baseUrl: String,
        // 3초: 같은 내부망(로컬 개발 기준 ai-api 포트 8081) 연결이라 3초 안에 안 붙으면
        // 네트워크·기동 문제로 간주해 즉시 실패한다(core-api AiHttpClientConfig와 동일 근거).
        @Value("\${devquest.ai.http.connect-timeout-ms:3000}") connectTimeoutMs: Long,
        // 150초: ai-api 내부 AiCallExecutor(max-retry=3)가 같은 HTTP 응답 사이클 안에서 백오프
        // 없이 최대 3회 순차 재시도한다. Anthropic 호출 1회 상한을 30초로 가정하면 3회 순차 합은
        // 90초 — 그 위에 안전 마진 60초(직렬화·네트워크 재전송·GC·디스패치 오버헤드 흡수용)를 더해
        // 150초로 설정한다(core-api AiHttpClientConfig KDoc과 동일 계산).
        @Value("\${devquest.ai.http.read-timeout-ms:150000}") readTimeoutMs: Long,
    ): RestClient = buildAiApiRestClient(baseUrl, connectTimeoutMs, readTimeoutMs)

    @Bean
    fun techInterviewPort(restClient: RestClient, objectMapper: ObjectMapper): TechInterviewPort =
        TechInterviewHttpAdapter(restClient, objectMapper)
}
