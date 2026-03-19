package com.devquest.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank

data class TechBlogRequestDto(
    @field:NotBlank val userId: String = "",
    @field:NotBlank val questId: String = "",
    @field:NotBlank val techTopic: String = "",
    @field:NotBlank val title: String = "",
    @field:NotBlank val content: String = ""
)
