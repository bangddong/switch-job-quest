package com.devquest.ai.controller

import com.devquest.ai.controller.request.CodingProblemGenerateRequest
import com.devquest.core.domain.model.coding.CodingProblemGenerationResult
import com.devquest.core.domain.port.CodingProblemGeneratorPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/coding-problem")
class CodingProblemController(
    private val codingProblemGeneratorPort: CodingProblemGeneratorPort,
) {

    @PostMapping("/generate")
    fun generate(@RequestBody request: CodingProblemGenerateRequest): CodingProblemGenerationResult =
        codingProblemGeneratorPort.generate(request.difficulty, request.language, request.category)
}
