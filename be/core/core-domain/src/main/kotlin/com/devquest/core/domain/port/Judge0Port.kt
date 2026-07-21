package com.devquest.core.domain.port

data class Judge0Result(
    val stdout: String = "",
    val stderr: String = "",
    val status: String = "",
    val passed: Boolean = false
)

// AiEvaluatorPort 마커를 상속하지 않는다: Judge0는 LLM 호출이 아니라 RapidAPI 기반 외부
// 코드채점(compile & run) 어댑터이므로, 서비스 분해 에픽에서 ai-service로 분리될 AI 컴퓨트
// 포트 범주에 속하지 않는다.
interface Judge0Port {
    fun execute(sourceCode: String, languageId: Int, stdin: String, expectedOutput: String): Judge0Result
}
