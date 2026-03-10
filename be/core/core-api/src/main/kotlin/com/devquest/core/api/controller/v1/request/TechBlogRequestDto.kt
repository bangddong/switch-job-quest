package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank

data class TechBlogRequestDto(
    val userId: String = "",
    val questId: String = "2-2",
    @field:NotBlank val techTopic: String = "",
    @field:NotBlank val title: String = "",
    @field:NotBlank val content: String = ""
)
