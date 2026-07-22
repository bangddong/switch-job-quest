package com.devquest.ai.controller

import com.devquest.ai.controller.request.CompanyFitAnalyzeRequest
import com.devquest.core.domain.model.evaluation.CompanyFitResult
import com.devquest.core.domain.port.CompanyFitEvaluatorPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/company-fit")
class CompanyFitController(
    private val companyFitEvaluatorPort: CompanyFitEvaluatorPort,
) {

    @PostMapping("/analyze")
    fun analyze(@RequestBody request: CompanyFitAnalyzeRequest): List<CompanyFitResult> =
        companyFitEvaluatorPort.analyze(request.preferences, request.companies)
}
