package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.support.AiEvaluationException
import com.devquest.core.domain.model.evaluation.CompanyFitResult
import com.devquest.core.domain.port.CompanyFitEvaluatorPort
import com.devquest.core.domain.port.CompanyInfo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class CompanyFitEvaluator(
    @Qualifier("bossChatClient") chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), CompanyFitEvaluatorPort {

    private val objectMapper = jacksonObjectMapper()

    private val log = LoggerFactory.getLogger(javaClass)

    private val template = PromptTemplate(ClassPathResource("prompts/company-fit.st"))

    override fun analyze(preferences: Map<String, String>, companies: List<CompanyInfo>): List<CompanyFitResult> {
        val preferencesText = preferences.entries.joinToString("\n") { (k, v) -> "- $k: $v" }
        val companiesText = companies.mapIndexed { i, c ->
            "${i + 1}. ${c.name}\n   - 문화: ${c.culture}\n   - 기술스택: ${c.techStack.joinToString(", ")}\n   - 규모: ${c.size}\n   - 설명: ${c.description}"
        }.joinToString("\n\n")

        val prompt = template.render(mapOf(
            "preferencesText" to preferencesText,
            "companiesText" to companiesText,
        ))

        val response = aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().content()
        }

        return try {
            objectMapper.readValue<List<CompanyFitResult>>(response)
        } catch (e: Exception) {
            log.error("회사 핏 분석 파싱 실패: ${e.message}")
            throw AiEvaluationException("회사 핏 분석 응답 파싱 실패", e)
        }
    }
}
