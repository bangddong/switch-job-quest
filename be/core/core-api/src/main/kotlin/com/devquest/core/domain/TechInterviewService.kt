package com.devquest.core.domain

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.TechInterviewPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TechInterviewService(
    private val techInterviewPort: TechInterviewPort,
    private val questProgressRecorder: QuestProgressRecorder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generateQuestion(userId: String, techStack: String): TechInterviewResult {
        log.info("기술 면접 질문 생성: userId=$userId, techStack=$techStack")
        return techInterviewPort.generateQuestions(techStack)
    }

    @Transactional
    fun evaluate(userId: String, techStack: String, questions: List<String>, answers: List<String>): TechInterviewResult {
        val result = techInterviewPort.evaluate(techStack, questions, answers)
        val xp = QuestXpPolicy.calculate(QuestConstants.TECH_INTERVIEW, result.passed)
        questProgressRecorder.record(userId, QuestConstants.TECH_INTERVIEW, 1, result.overallScore, result.passed, xp)
        log.info("기술 면접 평가 완료: userId=$userId, score=${result.overallScore}, passed=${result.passed}")
        return result
    }
}
