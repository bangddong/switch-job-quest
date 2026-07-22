package com.devquest.ai.controller

import com.devquest.ai.controller.request.JourneyReportGenerateRequest
import com.devquest.core.domain.model.evaluation.JourneyReportResult
import com.devquest.core.domain.port.JourneyReportPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/journey-report")
class JourneyReportController(
    private val journeyReportPort: JourneyReportPort,
) {

    @PostMapping("/generate")
    fun generate(@RequestBody request: JourneyReportGenerateRequest): JourneyReportResult =
        journeyReportPort.generate(
            request.companyName,
            request.targetPosition,
            request.questScores,
            request.totalXp,
            request.completedQuestCount,
        )
}
