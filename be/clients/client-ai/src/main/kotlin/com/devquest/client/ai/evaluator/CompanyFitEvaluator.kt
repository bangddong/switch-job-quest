package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.core.domain.support.AiEvaluationException
import com.devquest.core.domain.model.evaluation.CompanyFitResult
import com.devquest.core.domain.port.CompanyFitEvaluatorPort
import com.devquest.core.domain.port.CompanyInfo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class CompanyFitEvaluator(
    @Qualifier("bossChatClient") private val chatClient: ChatClient,
    private val aiCallExecutor: AiCallExecutor
) : CompanyFitEvaluatorPort {

    private val objectMapper = jacksonObjectMapper()

    private val log = LoggerFactory.getLogger(javaClass)

    override fun analyze(preferences: Map<String, String>, companies: List<CompanyInfo>): List<CompanyFitResult> {
        val preferencesText = preferences.entries.joinToString("\n") { (k, v) -> "- $k: $v" }
        val companiesText = companies.mapIndexed { i, c ->
            "${i + 1}. ${c.name}\n   - 문화: ${c.culture}\n   - 기술스택: ${c.techStack.joinToString(", ")}\n   - 규모: ${c.size}\n   - 설명: ${c.description}"
        }.joinToString("\n\n")

        val prompt = """
            개발자의 선호도를 기반으로 각 회사와의 핏 점수를 분석해주세요.

            ## 개발자 선호 사항
            $preferencesText
            ## 분석 대상 회사들
            $companiesText

            ## 평가 기준 (각 회사별 100점)
            - 문화 적합도: /25점 - 기술 적합도: /25점 - 성장 방향 적합도: /25점 - 라이프스타일 적합도: /25점

            반드시 다음 JSON 배열 형식으로만 응답하세요:
            [{"companyName": "...", "fitScore": 85, "fitGrade": "A", "cultureFit": 22, "techFit": 23, "growthFit": 20, "lifestyleFit": 20, "pros": ["..."], "cons": ["..."], "recommendation": "..."}]
        """.trimIndent()

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
