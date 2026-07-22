package com.devquest.ai.controller

import com.devquest.ai.controller.request.ActClearReportGenerateRequest
import com.devquest.core.domain.model.evaluation.ActClearReportResult
import com.devquest.core.domain.port.ActClearReportPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/act-clear-report")
class ActClearReportController(
    private val actClearReportPort: ActClearReportPort,
) {

    @PostMapping("/generate")
    fun generate(@RequestBody request: ActClearReportGenerateRequest): ActClearReportResult =
        actClearReportPort.generate(request.actId, request.actTitle, request.questScores)
}
