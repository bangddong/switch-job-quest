package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.v1.request.QuestCompleteRequestDto
import com.devquest.core.api.controller.v1.response.QuestHistoryResponseDto
import com.devquest.core.domain.ProgressService
import com.devquest.core.domain.model.ProgressResult
import com.devquest.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/progress")
class ProgressController(
    private val progressService: ProgressService
) {
    @GetMapping
    fun getProgress(@AuthenticationPrincipal userId: String): ApiResponse<ProgressResult> {
        return ApiResponse.success(progressService.getProgress(userId))
    }

    @PostMapping("/complete")
    fun completeQuest(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: QuestCompleteRequestDto,
    ): ApiResponse<Unit> {
        progressService.completeQuest(userId, request.questId, request.actId, request.earnedXp)
        return ApiResponse.success(Unit)
    }

    @GetMapping("/history")
    fun getHistory(@AuthenticationPrincipal userId: String): ApiResponse<List<QuestHistoryResponseDto>> {
        return ApiResponse.success(progressService.getHistory(userId).map { QuestHistoryResponseDto.from(it) })
    }

    @GetMapping("/history/{questId}")
    fun getQuestHistory(
        @AuthenticationPrincipal userId: String,
        @PathVariable questId: String,
    ): ApiResponse<List<QuestHistoryResponseDto>> {
        return ApiResponse.success(progressService.getQuestHistory(userId, questId).map { QuestHistoryResponseDto.from(it) })
    }
}
