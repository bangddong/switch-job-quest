package com.devquest.client.ai.judge0

import com.devquest.core.domain.port.Judge0Port
import com.devquest.core.domain.port.Judge0Result
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class Judge0Adapter(
    @Value("\${devquest.judge0.api-key:}") private val apiKey: String,
    @Value("\${devquest.judge0.api-host:judge0-ce.p.rapidapi.com}") private val apiHost: String
) : Judge0Port {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient = RestClient.builder()
        .baseUrl("https://judge0-ce.p.rapidapi.com")
        .build()

    override fun execute(sourceCode: String, languageId: Int, stdin: String): Judge0Result {
        if (apiKey.isBlank()) {
            log.warn("JUDGE0_API_KEY가 설정되지 않아 mock 응답을 반환합니다")
            return Judge0Result(stdout = "mock", status = "Accepted", passed = true)
        }

        return try {
            val requestBody = mapOf(
                "source_code" to sourceCode,
                "language_id" to languageId,
                "stdin" to stdin
            )

            val response = restClient.post()
                .uri("/submissions?base64_encoded=false&wait=true")
                .header("X-RapidAPI-Key", apiKey)
                .header("X-RapidAPI-Host", apiHost)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(Judge0Response::class.java)

            response?.toDomain(stdin) ?: Judge0Result(stderr = "응답 없음", status = "Internal Error")
        } catch (e: Exception) {
            log.error("Judge0 API 호출 실패: ${e.message}", e)
            Judge0Result(stderr = e.message ?: "알 수 없는 오류", status = "Internal Error")
        }
    }
}

data class Judge0Response(
    val stdout: String? = null,
    val stderr: String? = null,
    val status: Judge0StatusResponse? = null
) {
    fun toDomain(stdin: String): Judge0Result {
        val statusDesc = status?.description ?: "Unknown"
        return Judge0Result(
            stdout = stdout?.trim() ?: "",
            stderr = stderr ?: "",
            status = statusDesc,
            passed = statusDesc == "Accepted"
        )
    }
}

data class Judge0StatusResponse(
    val description: String = ""
)
