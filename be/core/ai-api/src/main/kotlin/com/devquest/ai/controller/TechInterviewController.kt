package com.devquest.ai.controller

import com.devquest.ai.controller.request.TechInterviewDailyQuestionRequest
import com.devquest.ai.controller.request.TechInterviewEvaluateRequest
import com.devquest.ai.controller.request.TechInterviewExplainFollowupRequest
import com.devquest.ai.controller.request.TechInterviewQuestionsRequest
import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.TechInterviewPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/tech-interview")
class TechInterviewController(
    private val techInterviewPort: TechInterviewPort,
) {

    @PostMapping("/questions")
    fun generateQuestions(@RequestBody request: TechInterviewQuestionsRequest): TechInterviewResult =
        techInterviewPort.generateQuestions(request.techStack)

    @PostMapping("/evaluate")
    fun evaluate(@RequestBody request: TechInterviewEvaluateRequest): TechInterviewResult =
        techInterviewPort.evaluate(request.techStack, request.questions, request.answers)

    /**
     * wire 계약 (실측 확정 — `TechInterviewWireFormatContractTest` 참고):
     * `produces = APPLICATION_JSON_VALUE`를 붙여도 반환 타입이 순수 `String`이면
     * `StringHttpMessageConverter`가 먼저 선택되어 **따옴표 없는 raw text**가 나간다
     * (`Content-Type: application/json`을 붙여도 실제 바디는 유효한 JSON이 아니다 — 헤더가
     * 거짓말을 하는 상태). 따라서 헤더를 바디 실제 형식과 일치시켜 `text/plain;charset=UTF-8`로
     * 명시한다. 한글 인코딩은 실측상 UTF-8로 정상 처리됨(mojibake 없음) — Boot 전체 컨텍스트가
     * `StringHttpMessageConverter` 기본 charset(ISO-8859-1)을 UTF-8로 재구성해 주기 때문이다.
     * Task 1.4의 RestClient는 이 엔드포인트를 `text/plain` 응답으로 취급해 `String`으로 그대로
     * 읽으면 된다(추가 JSON 역직렬화 불필요).
     */
    @PostMapping("/daily-question", produces = ["text/plain;charset=UTF-8"])
    fun generateDailyQuestion(@RequestBody request: TechInterviewDailyQuestionRequest): String =
        techInterviewPort.generateDailyQuestion(
            request.techStack,
            // Kotlin default 파라미터 소실 대응 — 필드 생략 시 서버측 기본값(emptyList())으로 복원
            request.recentQuestions ?: emptyList(),
        )

    // wire 계약은 generateDailyQuestion과 동일 — 위 KDoc 참고.
    @PostMapping("/explain-followup", produces = ["text/plain;charset=UTF-8"])
    fun explainFollowup(@RequestBody request: TechInterviewExplainFollowupRequest): String =
        techInterviewPort.explainFollowup(
            request.question,
            request.answer,
            request.feedback,
            request.userQuestion,
            request.modelAnswer,
        )
}
