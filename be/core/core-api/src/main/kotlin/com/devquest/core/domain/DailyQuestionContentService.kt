package com.devquest.core.domain

import com.devquest.core.domain.model.DailyQuestionContent
import com.devquest.core.domain.port.DailyQuestionContentPort
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.core.domain.port.TechQuestionBankPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
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
 *
 * ⚠️ **트랜잭션 경계 (QA F-1, `AiCheckService`/`CompanyService` KDoc과 동일 패턴)**:
 * [ensureTodayQuestion]에 의도적으로 `@Transactional`을 붙이지 않는다. 뱅크가 소진되면
 * `techInterviewPort.generateDailyQuestion`이 client-ai를 거쳐 실제 LLM 프로바이더로 나가는
 * 네트워크 호출을 한다(`TechInterviewEvaluator` → `BaseAiEvaluator.callAi` → `ChatClient`).
 * client-ai에는 timeout 설정이 없고 `AiCallExecutor`가 기본 3회 재시도하므로, 메서드 전체를
 * `@Transactional`로 감싸면 그 재시도 대기 내내 DB 커넥션 1개(prod HikariCP `maximum-pool-size: 10`)를
 * 붙잡아 커넥션 풀 고갈 위험이 커진다(Phase 1 Task 1.4, 커밋 `bd40c65`에서 이미 걷어낸 패턴의 재발).
 *
 * 원자성을 잃지 않는 이유: 이 메서드의 유일한 쓰기는 [dailyQuestionContentPort]`.save` 호출 한 번뿐이고,
 * `DailyQuestionContentAdapter.save`는 `JpaRepository.save`에 그대로 위임한다(db-core 확인 완료) —
 * Spring Data JPA의 `SimpleJpaRepository`가 `save`/조회 메서드마다 자체 `@Transactional`을 갖는다.
 * 즉 예전의 메서드 전체 `@Transactional`은 추가 원자성을 준 적이 없었다.
 *
 * **F-3**: `findToday` 조회와 `save` 사이는 원자적이지 않다 — 두 호출이 동시에 `findToday`에서
 * null을 보면 뒤의 `save()`가 `UNIQUE(question_date, mail_type)` 위반으로
 * [DataIntegrityViolationException]을 던진다. [ensureTodayQuestion]은 이를 잡아 기존 값을 재조회해
 * 반환한다(멱등성 최종 방어선). `replicas: 1` + 단일 스레드 스케줄러라 실무 발생 가능성은 낮지만,
 * UNIQUE 제약을 걸어두고 그 예외를 처리하지 않으면 제약이 "보호"가 아니라 "장애"가 된다.
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

        return try {
            val saved = dailyQuestionContentPort.save(content)
            log.info("오늘의 질문 생성 완료: date=$today, source=${saved.source}")
            saved
        } catch (e: DataIntegrityViolationException) {
            // F-3: findToday~save 사이 경합으로 다른 실행이 먼저 저장했다 — 그 값을 승자로 채택한다.
            log.warn("오늘의 질문 저장 중 UNIQUE 충돌 — 동시 생성으로 판단, 기존 값 재조회: date=$today")
            dailyQuestionContentPort.findToday(MAIL_TYPE, today) ?: throw e
        }
    }
}
