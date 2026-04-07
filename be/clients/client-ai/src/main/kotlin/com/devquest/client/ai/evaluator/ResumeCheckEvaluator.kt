package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.core.domain.model.evaluation.ResumeCheckResult
import com.devquest.core.domain.port.ResumeEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class ResumeCheckEvaluator(
    private val chatClient: ChatClient,
    private val aiCallExecutor: AiCallExecutor
) : ResumeEvaluatorPort {

    private val template = PromptTemplate(ClassPathResource("prompts/resume-check.st"))

    override fun evaluate(targetCompany: String, targetJd: String, resumeContent: String): ResumeCheckResult {
        val prompt = template.render(mapOf(
            "targetCompany" to targetCompany,
            "targetJd" to targetJd.take(500),
            "resumeContent" to resumeContent,
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(ResumeCheckResult::class.java)
        }
    }
}
