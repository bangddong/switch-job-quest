package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.PassCriteriaPolicy
import com.devquest.core.domain.model.evaluation.JdAnalysisResult
import com.devquest.core.domain.port.JdAnalysisEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class JdAnalysisEvaluator(
    chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), JdAnalysisEvaluatorPort {

    private val systemTemplate = PromptTemplate(ClassPathResource("prompts/jd-analysis-system.st"))
    private val userTemplate = PromptTemplate(ClassPathResource("prompts/jd-analysis-user.st"))

    override fun analyze(companyName: String, jobDescription: String, userSkills: List<String>, userExperiences: List<String>): JdAnalysisResult {
        val systemPrompt = systemTemplate.render()
        val userPrompt = userTemplate.render(mapOf(
            "companyName" to companyName,
            "jobDescription" to wrapUserContent(jobDescription),
            "userSkills" to userSkills.joinToString(", "),
            "userExperiences" to wrapUserContent(userExperiences.joinToString("\n")),
        ))

        return aiCallExecutor.execute(this.javaClass.simpleName, modelName) {
            val content = callAi(systemPrompt, userPrompt)
            parseContent(content, JdAnalysisResult::class.java)
                ?.let { it.copy(passed = PassCriteriaPolicy.evaluate(it.overallMatchScore)) }
        }
    }
}
