package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.v1.request.CodeSubmitRequestDto
import com.devquest.core.api.controller.v1.request.CodingHintRequestDto
import com.devquest.core.domain.CodingQuestService
import com.devquest.core.domain.model.coding.TestCase
import com.devquest.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class CodingProblemResponse(
    val id: Long,
    val title: String,
    val description: String,
    val difficulty: String,
    val language: String,
    val testCases: List<TestCase>
)

@RestController
@RequestMapping("/api/v1/coding")
class CodingQuestController(
    private val codingQuestService: CodingQuestService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/problem")
    fun generateProblem(
        @AuthenticationPrincipal userId: String,
        @RequestParam(defaultValue = "JAVA") language: String
    ): ApiResponse<*> {
        val problem = codingQuestService.generateProblem(userId, language)
        val response = CodingProblemResponse(
            id = problem.id,
            title = problem.title,
            description = problem.description,
            difficulty = problem.difficulty,
            language = problem.language,
            testCases = problem.testCases
        )
        return ApiResponse.success(response)
    }

    @PostMapping("/submit")
    fun submitCode(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: CodeSubmitRequestDto
    ): ApiResponse<*> {
        val result = codingQuestService.submitCode(userId, request.problemId, request.language, request.userCode)
        return ApiResponse.success(result)
    }

    @GetMapping("/level")
    fun getLevel(@AuthenticationPrincipal userId: String): ApiResponse<*> {
        val level = codingQuestService.getLevel(userId)
        return ApiResponse.success(mapOf("level" to level))
    }

    @PostMapping("/hint")
    fun getHint(
        @AuthenticationPrincipal _userId: String,
        @Valid @RequestBody request: CodingHintRequestDto
    ): ApiResponse<*> {
        val result = codingQuestService.getHint(request.problemId, request.title, request.description, request.hintLevel)
        return ApiResponse.success(result)
    }
}
