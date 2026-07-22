package com.devquest.core.domain

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.TechInterviewPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * ⚠️ **트랜잭션 경계 (Phase 1 Task 1.4b/1.5)**: [evaluate]는 의도적으로 `@Transactional`을 붙이지
 * 않는다 — AI 호출 뒤 유일한 DB 부수효과는 [QuestProgressRecorder.record] 호출 하나뿐이고, 그
 * 메서드 자체가 별도 빈에서 독립적으로 `@Transactional`이다. 자세한 근거는 [AiCheckService] 상단
 * KDoc 참고(동일 패턴).
 */
@Service
class TechInterviewService(
    private val techInterviewPort: TechInterviewPort,
    private val questProgressRecorder: QuestProgressRecorder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generateQuestion(userId: String?, techStack: String): TechInterviewResult {
        val userLabel = userId ?: "guest"
        log.info("기술 면접 질문 생성: userId={}, techStack={}", userLabel, techStack)
        return techInterviewPort.generateQuestions(techStack)
    }

    fun evaluate(userId: String?, techStack: String, questions: List<String>, answers: List<String>): TechInterviewResult {
        val result = techInterviewPort.evaluate(techStack, questions, answers)
        val userLabel = userId ?: "guest"
        if (userId != null) {
            val xp = QuestXpPolicy.calculate(QuestConstants.TECH_INTERVIEW, result.passed)
            questProgressRecorder.record(userId, QuestConstants.TECH_INTERVIEW, 1, result.overallScore, result.passed, xp)
        }
        log.info("기술 면접 평가 완료: userId={}, score={}, passed={}", userLabel, result.overallScore, result.passed)
        return result
    }
}
