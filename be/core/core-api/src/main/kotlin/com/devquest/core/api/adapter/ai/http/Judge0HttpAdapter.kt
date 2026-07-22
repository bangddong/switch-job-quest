package com.devquest.core.api.adapter.ai.http

import com.devquest.core.domain.port.Judge0Port
import com.devquest.core.domain.port.Judge0Result
import tools.jackson.databind.ObjectMapper
import org.springframework.web.client.RestClient

/**
 * `Judge0Port`의 HTTP 어댑터 — ai-api `POST /internal/ai/judge0/execute` 호출.
 *
 * `Judge0Port`는 `AiEvaluatorPort` 마커를 상속하지 않는 비-LLM(RapidAPI 코드채점) 포트지만,
 * ai-api가 이미 이를 노출한다(Task 0.1 결정 + Task 1.1 구현 확정). "외부 컴퓨트 위임"이라는 성격이
 * LLM 포트와 같고, 전송 계층을 전환 가능한 상태로 함께 유지하지 않으면 17개 AI 포트만 HTTP로 뽑히고
 * Judge0만 영구히 in-process에 남는 비일관 상태가 되므로 이 태스크에서 나머지 17개와 동일하게
 * 전환 스위치 대상에 포함한다.
 */
class Judge0HttpAdapter(
    restClient: RestClient,
    objectMapper: ObjectMapper,
) : BaseAiHttpAdapter(restClient, objectMapper), Judge0Port {

    override fun execute(sourceCode: String, languageId: Int, stdin: String, expectedOutput: String): Judge0Result =
        postJson(
            "/internal/ai/judge0/execute",
            Judge0ExecuteHttpRequest(sourceCode, languageId, stdin, expectedOutput),
        )
}

private data class Judge0ExecuteHttpRequest(
    val sourceCode: String,
    val languageId: Int,
    val stdin: String,
    val expectedOutput: String,
)
