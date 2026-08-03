package com.devquest.core.domain

import com.devquest.core.domain.model.DailyQuestionContent
import com.devquest.core.domain.port.DailyQuestionContentPort
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.core.domain.port.TechQuestionBankPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

/**
 * 오늘의 기술 면접 질문 "생성"을 담당한다.
 *
 * 메일 발송(DailyMailScheduler)의 부산물이 아니라 독립된 콘텐츠 생성 책임이다 —
 * 유저 수·MAIL_ENABLED 여부와 무관하게 [ensureTodayQuestion]이 호출되면 오늘의 질문이 보장된다.
 *
 * 이 서비스는 추후 별도 라이브러리 모듈로 옮겨질 수 있으므로 core-api 고유 개념
 * (ApiResponse, CoreException 등)에 의존하지 않는다.
 */
@Service
class DailyQuestionContentService(
    private val dailyQuestionContentPort: DailyQuestionContentPort,
    private val techQuestionBankPort: TechQuestionBankPort,
    private val techInterviewPort: TechInterviewPort,
    @Value("\${devquest.daily-question.tech-stack}") private val techStack: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAIL_TYPE = "TECH_INTERVIEW"

        // 질문 뱅크가 총 26개(V10 5개 + V11 21개)뿐이라, 최근 질문 제외 윈도우가 뱅크 크기 이상이면
        // 뱅크가 주기적으로 완전히 소진되어 AI 폴백(generateDailyQuestion, 비용 발생)이 매일 돌게 된다.
        // 20일로 두면 뱅크에서 항상 최소 6개 이상이 후보로 남아 폴백 없이 동작한다.
        private const val RECENT_QUESTION_WINDOW_DAYS = 20L
    }

    /**
     * 오늘의 질문이 이미 있으면 그대로 반환하고(멱등), 없으면 뱅크 → AI 순서로 골라 저장한다.
     */
    @Transactional
    fun ensureTodayQuestion(): DailyQuestionContent {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        dailyQuestionContentPort.findToday(MAIL_TYPE, today)?.let { existing ->
            log.info("오늘의 질문 이미 존재 — 재생성 skip: date=$today")
            return existing
        }

        val since = today.minusDays(RECENT_QUESTION_WINDOW_DAYS)
        val recentQuestions = dailyQuestionContentPort.findQuestionsSince(MAIL_TYPE, since)
        val bankQuestion = techQuestionBankPort.findUnused(recentQuestions)
        val content = if (bankQuestion != null) {
            log.info("질문 뱅크에서 질문 채택: category=${bankQuestion.category}")
            DailyQuestionContent(
                questionDate = today,
                mailType = MAIL_TYPE,
                question = bankQuestion.question,
                source = "BANK",
                category = bankQuestion.category,
            )
        } else {
            log.info("질문 뱅크 소진 — AI로 질문 생성")
            val question = techInterviewPort.generateDailyQuestion(techStack, recentQuestions)
            DailyQuestionContent(
                questionDate = today,
                mailType = MAIL_TYPE,
                question = question,
                source = "AI",
            )
        }

        val saved = dailyQuestionContentPort.save(content)
        log.info("오늘의 질문 생성 완료: date=$today, source=${saved.source}")
        return saved
    }
}
