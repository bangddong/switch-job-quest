package com.devquest.core.domain

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.DailyMailLogPort
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.core.support.error.CoreException
import com.devquest.core.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

@Service
class DailyQuestionService(
    private val dailyMailLogPort: DailyMailLogPort,
    private val techInterviewPort: TechInterviewPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getTodayQuestion(): String {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        return dailyMailLogPort.findTodayQuestion("TECH_INTERVIEW", today)
            ?: throw CoreException(ErrorType.DAILY_QUESTION_NOT_FOUND)
    }

    fun evaluate(question: String, answer: String): TechInterviewResult {
        val result = techInterviewPort.evaluate("Java,Spring Boot,JPA", listOf(question), listOf(answer))
        log.info("데일리 질문 평가 완료: score=${result.overallScore}")
        return result
    }

    fun explain(question: String, answer: String, feedback: String, userQuestion: String, modelAnswer: String?): String {
        val explanation = techInterviewPort.explainFollowup(question, answer, feedback, userQuestion, modelAnswer)
        log.info("데일리 질문 후속 설명 완료")
        return explanation
    }
}
