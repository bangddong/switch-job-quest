package com.devquest.ai.controller.request

import com.devquest.core.domain.port.CompanyInfo

data class CompanyFitAnalyzeRequest(
    val preferences: Map<String, String>,
    val companies: List<CompanyInfo>,
)
