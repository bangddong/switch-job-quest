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

    private val template = PromptTemplate(ClassPathResource("prompts/skill-assessment.st"))

    override fun evaluate(skills: List<String>, targetRole: String): SkillAssessmentResult {
        val skillsText = skills.joinToString("\n") { "- $it" }

        val prompt = template.render(mapOf(
            "skillsText" to skillsText,
            "targetRole" to targetRole,
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(SkillAssessmentResult::class.java)
        }
    }
}
