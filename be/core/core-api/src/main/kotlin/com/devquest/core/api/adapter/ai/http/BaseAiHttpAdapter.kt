package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.support.AiEvaluationException
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.util.StreamUtils
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.nio.charset.StandardCharsets

/**
 * ai-api HTTP 어댑터 공통 로직 — 18개 AI 포트(17개 `AiEvaluatorPort` + `Judge0Port`)의 HTTP 구현체가
 * 이 클래스를 상속해 요청/응답/에러 처리를 공유한다. Phase 1 Task 1.4a.
 *
 * ⚠️ **재시도를 하지 않는다** (계획 문서 함정 (b) — 재시도 이중화 금지). `client-ai`의
 * `AiCallExecutor`(`devquest.ai.max-retry`=3)가 이미 ai-api 프로세스 **안에서** 재시도한다. 이
 * 어댑터가 여기서 또 재시도하면 최대 3×3=9회 실제 LLM 호출이 되어 비용이 곱해진다. 실패는 재시도
 * 없이 그대로 [AiEvaluationException]으로 전파한다.
 *
 * ⚠️ **Accept 헤더를 강제하지 않는다** (계획 문서 함정 (c) — 406 회피). ai-api 엔드포인트 24개 중
 * 2개(`/tech-interview/daily-question`, `/explain-followup`)만 `text/plain;charset=UTF-8`을 내려주고
 * 나머지 22개는 `application/json`을 내려준다. 공용 클라이언트에 `.accept(MediaType.APPLICATION_JSON)`을
 * 균일하게 걸면 그 2개에서 406 Not Acceptable이 난다. 이 클래스는 응답을 **항상 `String`으로만**
 * 요청한다 — `StringHttpMessageConverter`는 `text/plain`과 `MediaType.ALL`(모든 타입 와일드카드)을
 * 지원 미디어 타입으로 등록해 어떤 `Content-Type`이든 원문 텍스트로 읽을 수 있고(Jackson 컨버터는
 * 대상 타입이 `String`이면 절대 `canRead`를 claim하지 않아 경쟁이 없음), 이로 인해 RestClient가
 * 구성하는 Accept 헤더는 `text/plain` + 와일드카드가 되어 JSON·text/plain 응답 양쪽 모두를
 * 만족시킨다. JSON이 필요한 22개
 * 엔드포인트는 이 원문을 [postJson]이 Jackson으로 파싱하고, text/plain인 2개는 [postText]가 원문을
 * 그대로 반환한다. 406 미발생은 [com.devquest.core.api.adapter.ai.http.support.FakeAiApiServer]를 쓰는
 * 실측 테스트(`AiHttpAdapterTimeoutAndAcceptHeaderTest`)로 증명한다.
 *
 * ⚠️ **요청도 항상 `String`으로 직접 만든다** — Spring Boot 4.x는 기본적으로 Jackson 3
 * (`tools.jackson.databind.json.JsonMapper`)을 자동 구성한다. ai-api 서버(별도 프로세스)도 Boot 4
 * 기본값 그대로 Jackson 3로 (역)직렬화하므로, 이 어댑터가 Jackson 2로 요청/응답을 다루면 **core가 J2로
 * 쓰고 ai-api가 J3로 읽는 비대칭**이 생긴다(Task 1.4a 시점의 QA MEDIUM #1 — "프레임워크 버전과
 * 무관하게"는 틀린 목표였고, 옳은 목표는 ai-api의 실제 선택인 J3와 **일치**시키는 것이다). 그래서
 * [objectMapper](Boot가 자동구성하는 J3 `ObjectMapper`를 그대로 주입받는다 — Kotlin 모듈 등록 여부는
 * `AiHttpJacksonV3ContextTest`로 실측 확인됨)로 요청 본문을 직접 `writeValueAsString`한 뒤
 * `Content-Type: application/json`을 명시하고 `String`으로 전송한다. `StringHttpMessageConverter`는
 * 지원 미디어 타입에 `MediaType.ALL`을 포함해 임의의 `Content-Type` 헤더를 붙인 `String` 바디도 그대로
 * 써낼 수 있으므로, RestClient가 자동 감지하는 JSON 컨버터가 어떤 버전이든 무관하게 동작한다.
 *
 * **에러 매핑 정책**: ai-api가 4xx/5xx를 반환하면 Boot 기본 에러 바디
 * (`{timestamp,status,error,path,message}`, ai-api의 `spring.web.error.include-message: always` 설정으로
 * `message` 필드가 채워짐)를 읽어 원인 메시지를 [AiEvaluationException]에 실어 던진다. 네트워크 오류
 * (연결 실패·커넥트/리드 타임아웃 등 `RestClientException`)도 동일하게 [AiEvaluationException]으로
 * 단일화한다 — in-process 경로(`AiCallExecutor`)도 실패 시 동일한 예외 타입을 던지므로, 전송 계층이
 * 바뀌어도 소비 서비스·`ApiControllerAdvice`가 인지하는 예외 타입은 동일하게 유지된다(parity).
 */
abstract class BaseAiHttpAdapter(
    protected val restClient: RestClient,
    protected val objectMapper: ObjectMapper,
) {

    /** JSON 응답을 T로 역직렬화한다. `tools.jackson.module.kotlin.readValue` reified 확장으로 `List<X>` 같은 제네릭 타입 소거를 방지한다. */
    protected inline fun <reified T> postJson(path: String, requestBody: Any): T {
        val raw = fetchRaw(path, requestBody)
        return try {
            objectMapper.readValue<T>(raw)
        } catch (e: Exception) {
            throw AiEvaluationException("ai-api 응답 파싱 실패($path): ${e.message}", e)
        }
    }

    /** text/plain 응답(순수 String 반환 포트: daily-question·explain-followup)을 원문 그대로 반환한다. */
    protected fun postText(path: String, requestBody: Any): String = fetchRaw(path, requestBody)

    protected fun fetchRaw(path: String, requestBody: Any): String {
        try {
            val requestJson = objectMapper.writeValueAsString(requestBody)
            return restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .retrieve()
                .onStatus({ it.isError }) { _, response -> handleError(path, response) }
                .body(String::class.java)
                ?: throw AiEvaluationException("ai-api 응답 본문 없음: $path")
        } catch (e: AiEvaluationException) {
            throw e
        } catch (e: RestClientException) {
            throw AiEvaluationException("ai-api 호출 실패($path): ${e.message}", e)
        }
    }

    private fun handleError(path: String, response: ClientHttpResponse) {
        val rawBody = runCatching {
            StreamUtils.copyToString(response.body, StandardCharsets.UTF_8)
        }.getOrDefault("")
        val message = runCatching {
            objectMapper.readValue(rawBody, AiApiErrorResponse::class.java).message
        }.getOrNull()
        throw AiEvaluationException(
            message ?: "ai-api 호출 실패($path): HTTP ${response.statusCode.value()} $rawBody"
        )
    }
}

/** ai-api(Boot 기본 에러 핸들러 `BasicErrorController`)의 에러 응답 바디 계약. */
data class AiApiErrorResponse(
    val timestamp: String? = null,
    val status: Int? = null,
    val error: String? = null,
    val path: String? = null,
    val message: String? = null,
)
