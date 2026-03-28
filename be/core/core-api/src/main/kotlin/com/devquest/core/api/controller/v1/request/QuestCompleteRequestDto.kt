package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class QuestCompleteRequestDto(
    @field:NotBlank val userId: String,
    @field:NotBlank val questId: String,
    @field:Positive val actId: Int,
    @field:Positive val earnedXp: Int,
)
