package com.devquest.core.api.controller.v1.request

data class CompanyInfoDto(
    val name: String = "",
    val culture: String = "",
    val techStack: List<String> = emptyList(),
    val size: String = "",
    val description: String = ""
)

data class CompanyFitRequestDto(
    val userId: String = "",
    val preferences: Map<String, String> = emptyMap(),
    val companies: List<CompanyInfoDto> = emptyList()
)
