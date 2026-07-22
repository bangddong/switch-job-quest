package com.devquest.ai.controller

import com.devquest.ai.controller.request.Judge0ExecuteRequest
import com.devquest.core.domain.port.Judge0Port
import com.devquest.core.domain.port.Judge0Result
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Judge0Port는 비-LLM(RapidAPI 코드채점) 포트라 AiEvaluatorPort 마커를 상속하지 않지만
 * "외부 컴퓨트 위임"이라는 성격이 같아 ai-api에 함께 노출한다 (계획 문서 Task 0.1 추천 결정).
 */
@RestController
@RequestMapping("/internal/ai/judge0")
class Judge0Controller(
    private val judge0Port: Judge0Port,
) {

    @PostMapping("/execute")
    fun execute(@RequestBody request: Judge0ExecuteRequest): Judge0Result =
        judge0Port.execute(request.sourceCode, request.languageId, request.stdin, request.expectedOutput)
}
