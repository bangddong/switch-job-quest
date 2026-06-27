package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.client.ai.support.ConferenceReferenceLoader
import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.TechInterviewPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class TechInterviewEvaluator(
    chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor,
    private val conferenceReferenceLoader: ConferenceReferenceLoader,
) : BaseAiEvaluator(chatClient, aiCallExecutor), TechInterviewPort {

    private val questionsSystemTemplate = PromptTemplate(ClassPathResource("prompts/tech-interview-questions-system.st"))
    private val questionsUserTemplate = PromptTemplate(ClassPathResource("prompts/tech-interview-questions-user.st"))
    private val evaluateSystemTemplate = PromptTemplate(ClassPathResource("prompts/tech-interview-evaluate-system.st"))
    private val evaluateUserTemplate = PromptTemplate(ClassPathResource("prompts/tech-interview-evaluate-user.st"))

    override fun generateQuestions(techStack: String): TechInterviewResult {
        val systemPrompt = questionsSystemTemplate.render()
        val userPrompt = questionsUserTemplate.render(mapOf("techStack" to wrapUserContent(techStack)))
        return aiCallExecutor.execute(this.javaClass.simpleName, modelName) {
            val content = callAi(systemPrompt, userPrompt)
            parseContent(content, TechInterviewResult::class.java)
        }
    }

    private val dailyQuestionSystemTemplate = PromptTemplate(ClassPathResource("prompts/daily-question-system.st"))
    private val dailyQuestionUserTemplate = PromptTemplate(ClassPathResource("prompts/daily-question-user.st"))

    override fun generateDailyQuestion(techStack: String, recentQuestions: List<String>): String {
        val systemPrompt = dailyQuestionSystemTemplate.render()
        val userPrompt = dailyQuestionUserTemplate.render(mapOf(
            "techStack" to wrapUserContent(techStack),
            "recentQuestions" to if (recentQuestions.isEmpty()) "없음"
                else recentQuestions.mapIndexed { i, q -> "${i + 1}. $q" }.joinToString("\n"),
        ))
        return aiCallExecutor.execute(this.javaClass.simpleName, modelName) {
            callAi(systemPrompt, userPrompt)
        }
    }

    override fun evaluate(techStack: String, questions: List<String>, answers: List<String>): TechInterviewResult {
        val questionText = questions.joinToString(" ")
        val refs = conferenceReferenceLoader.findByQuestion(questionText)
        val systemPrompt = evaluateSystemTemplate.render(mapOf("conferenceReferences" to refs))
        val questionsAndAnswers = questions.zip(answers).joinToString("\n\n") { (q, a) ->
            "Q: $q\nA: ${a.take(1000)}"
        }
        val userPrompt = evaluateUserTemplate.render(mapOf(
            "techStack" to techStack,
            "questionsAndAnswers" to wrapUserContent(questionsAndAnswers),
        ))
        return aiCallExecutor.execute(this.javaClass.simpleName, modelName) {
            val content = callAi(systemPrompt, userPrompt)
            parseContent(content, TechInterviewResult::class.java)
        }
    }
}
