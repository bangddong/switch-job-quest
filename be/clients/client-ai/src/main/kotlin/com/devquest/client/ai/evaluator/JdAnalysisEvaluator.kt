package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.core.domain.model.evaluation.JdAnalysisResult
import com.devquest.core.domain.port.JdAnalysisEvaluatorPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class JdAnalysisEvaluator(
    private val chatClient: ChatClient,
    private val aiCallExecutor: AiCallExecutor
) : JdAnalysisEvaluatorPort {

    private val template = PromptTemplate(ClassPathResource("prompts/jd-analysis.st"))

    override fun analyze(companyName: String, jobDescription: String, userSkills: List<String>, userExperiences: List<String>): JdAnalysisResult {
        val prompt = template.render(mapOf(
            "companyName" to companyName,
            "jobDescription" to jobDescription,
            "userSkills" to userSkills.joinToString(", "),
            "userExperiences" to userExperiences.joinToString("\n"),
        ))

        return aiCallExecutor.execute {
            chatClient.prompt().user(prompt).call().entity(JdAnalysisResult::class.java)
        }
    }
}
