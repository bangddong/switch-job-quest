package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.model.evaluation.ResumeCheckResult
import com.devquest.core.domain.port.ResumeEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class ResumeCheckEvaluator(
    chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), ResumeEvaluatorPort {

    private val systemTemplate = PromptTemplate(ClassPathResource("prompts/resume-check-system.st"))
    private val userTemplate = PromptTemplate(ClassPathResource("prompts/resume-check-user.st"))

    override fun evaluate(targetCompany: String, targetJd: String, resumeContent: String): ResumeCheckResult {
        val systemPrompt = systemTemplate.render()
        val userPrompt = userTemplate.render(mapOf(
            "targetCompany" to targetCompany,
            "targetJd" to targetJd.take(500),
            "resumeContent" to resumeContent,
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().system(systemPrompt).user(userPrompt).call().entity(ResumeCheckResult::class.java)
                ?.let { it.copy(passed = it.overallScore >= 70) }
        }
    }
}
