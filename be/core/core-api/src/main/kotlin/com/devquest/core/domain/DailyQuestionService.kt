package com.devquest.core.domain

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.DailyQuestionContentPort
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.core.support.error.CoreException
import com.devquest.core.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

@Service
class DailyQuestionService(
    private val dailyQuestionContentPort: DailyQuestionContentPort,
    private val techInterviewPort: TechInterviewPort,
    // Port가 아닌 구체 클래스 주입 — be/CLAUDE.md 컨벤션 위반이지만 선례가 있다
    // (DailyMailScheduler도 동일하게 주입한다). DailyQuestionContentService는 Stage B에서
    // 별도 라이브러리 모듈로 이동 예정이라 지금 Port를 새로 뽑으면 그때 다시 손대야 한다.
    private val dailyQuestionContentService: DailyQuestionContentService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Phase 2 Stage A: 오늘 행이 없으면 뱅크 전용으로 lazy 생성한다(AI 폴백 없음).
     * 뱅크까지 소진이면 404 — AI 폴백은 09:00 cron(`DailyQuestionContentService.ensureTodayQuestion`)에만 남긴다.
     */
    fun getTodayQuestion(): String {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        return dailyQuestionContentPort.findToday("TECH_INTERVIEW", today)?.question
            ?: dailyQuestionContentService.ensureTodayQuestionFromBank()?.question
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
