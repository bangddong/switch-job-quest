package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class TechInterviewEvaluateRequestDto(
    @field:NotBlank val techStack: String = "",
    @field:Size(min = 1) val questions: List<String> = emptyList(),
    @field:Size(min = 1) val answers: List<String> = emptyList(),
) {
    @AssertTrue(message = "질문과 답변 수가 일치해야 합니다")
    fun isQuestionsAnswersMatched(): Boolean = questions.size == answers.size
}
