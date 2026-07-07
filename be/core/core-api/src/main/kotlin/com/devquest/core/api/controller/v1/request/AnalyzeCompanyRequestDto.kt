package com.devquest.core.api.controller.v1.request

data class AnalyzeCompanyRequestDto(
    val userSkills: List<String> = emptyList(),
    val userExperiences: List<String> = emptyList(),
)
