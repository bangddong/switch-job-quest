package com.devquest.client.ai.evaluator

import com.devquest.client.ai.support.AiCallExecutor
import com.devquest.client.ai.support.BaseAiEvaluator
import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.TechInterviewPort
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class TechInterviewEvaluator(
    chatClient: ChatClient,
    aiCallExecutor: AiCallExecutor
) : BaseAiEvaluator(chatClient, aiCallExecutor), TechInterviewPort {

    private val questionsSystemTemplate = PromptTemplate(ClassPathResource("prompts/tech-interview-questions-system.st"))
    private val questionsUserTemplate = PromptTemplate(ClassPathResource("prompts/tech-interview-questions-user.st"))
    private val evaluateSystemTemplate = PromptTemplate(ClassPathResource("prompts/tech-interview-evaluate-system.st"))
    private val evaluateUserTemplate = PromptTemplate(ClassPathResource("prompts/tech-interview-evaluate-user.st"))

    override fun generateQuestions(techStack: String): TechInterviewResult {
        val systemPrompt = questionsSystemTemplate.render()
        val userPrompt = questionsUserTemplate.render(mapOf("techStack" to techStack))
        return aiCallExecutor.execute {
            chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(TechInterviewResult::class.java)
        }
    }

    override fun generateDailyQuestion(techStack: String): String {
        val systemPrompt = "당신은 백엔드 개발자 기술면접 전문가입니다. 질문 텍스트만 반환하세요. 다른 설명이나 번호를 포함하지 마세요."
        val userPrompt = "다음 기술 스택을 사용하는 백엔드 개발자를 위한 기술면접 질문 1개를 생성해주세요. 질문 텍스트만 반환하세요.\n기술 스택: $techStack"
        return aiCallExecutor.execute {
            chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content()
        }
    }

    override fun evaluate(techStack: String, questions: List<String>, answers: List<String>): TechInterviewResult {
        val systemPrompt = evaluateSystemTemplate.render()
        val questionsAndAnswers = questions.zip(answers).joinToString("\n\n") { (q, a) ->
            "Q: $q\nA: $a"
        }
        val userPrompt = evaluateUserTemplate.render(mapOf(
            "techStack" to techStack,
            "questionsAndAnswers" to questionsAndAnswers,
        ))
        return aiCallExecutor.execute {
            chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(TechInterviewResult::class.java)
        }
    }
}
