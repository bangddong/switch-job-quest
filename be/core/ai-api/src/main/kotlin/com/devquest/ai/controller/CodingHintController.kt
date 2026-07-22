package com.devquest.ai.controller

import com.devquest.ai.controller.request.CodingHintGetRequest
import com.devquest.core.domain.model.coding.CodingHint
import com.devquest.core.domain.port.CodingHintPort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ai/coding-hint")
class CodingHintController(
    private val codingHintPort: CodingHintPort,
) {

    @PostMapping("/get")
    fun getHint(@RequestBody request: CodingHintGetRequest): CodingHint =
        codingHintPort.getHint(request.problemId, request.title, request.description, request.hintLevel)
}
