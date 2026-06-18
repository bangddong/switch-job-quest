package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.model.evaluation.SkillAssessmentResult
import com.devquest.core.domain.port.SkillAssessmentPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class SkillAssessmentEvaluator(
    chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), SkillAssessmentPort {

    private val systemTemplate = PromptTemplate(ClassPathResource("prompts/skill-assessment-system.st"))
    private val userTemplate = PromptTemplate(ClassPathResource("prompts/skill-assessment-user.st"))

    override fun evaluate(skills: List<String>, targetRole: String): SkillAssessmentResult {
        val skillsText = skills.joinToString("\n") { "- $it" }

        val systemPrompt = systemTemplate.render()
        val userPrompt = userTemplate.render(mapOf(
            "skillsText" to skillsText,
            "targetRole" to targetRole,
        ))

        return aiCallExecutor.execute(this.javaClass.simpleName, modelName) {
            val content = callAi(systemPrompt, userPrompt)
            parseContent(content, SkillAssessmentResult::class.java)
        }
    }
}
