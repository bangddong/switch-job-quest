package com.devquest.daily.service

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.DailyQuestionContentPort
import com.devquest.core.domain.port.DailyQuestionGeneratorPort
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.daily.support.DailyQuestionNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

/**
 * core-api의 `DailyQuestionService`와 동일한 오케스트레이션을 daily-api 자체에서 수행한다(D-007).
 * core-api를 의존할 수 없어 재사용이 불가능하므로 로직을 그대로 복제했다 — 두 구현이 갈라지지
 * 않도록 core-api `DailyQuestionService`가 바뀌면 이쪽도 함께 갱신해야 한다(알려진 중복, 기록용).
 */
@Service
class DailyQuestionApiService(
    private val dailyQuestionContentPort: DailyQuestionContentPort,
    private val techInterviewPort: TechInterviewPort,
    private val dailyQuestionGeneratorPort: DailyQuestionGeneratorPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAIL_TYPE = "TECH_INTERVIEW"

        // core-api DailyQuestionService.evaluate와 동일하게 하드코딩 — 요청 DTO에 techStack 필드가
        // 없다(core-api 계약을 그대로 따름).
        private const val EVALUATE_TECH_STACK = "Java,Spring Boot,JPA"
    }

    /**
     * 오늘 행이 없으면 뱅크 전용으로 lazy 생성한다(AI 폴백 없음, 함정 ⑨). 뱅크까지 소진이면
     * [DailyQuestionNotFoundException] — 컨트롤러 어드바이스가 404로 매핑한다.
     */
    fun getTodayQuestion(): String {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        return dailyQuestionContentPort.findToday(MAIL_TYPE, today)?.question
            ?: dailyQuestionGeneratorPort.ensureTodayQuestionFromBank()?.question
            ?: throw DailyQuestionNotFoundException()
    }

    fun evaluate(question: String, answer: String): TechInterviewResult {
        val result = techInterviewPort.evaluate(EVALUATE_TECH_STACK, listOf(question), listOf(answer))
        log.info("데일리 질문 평가 완료: score=${result.overallScore}")
        return result
    }

    fun explain(question: String, answer: String, feedback: String, userQuestion: String, modelAnswer: String?): String {
        val explanation = techInterviewPort.explainFollowup(question, answer, feedback, userQuestion, modelAnswer)
        log.info("데일리 질문 후속 설명 완료")
        return explanation
    }
}
