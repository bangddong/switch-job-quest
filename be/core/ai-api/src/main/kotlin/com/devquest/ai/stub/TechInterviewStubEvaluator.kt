package com.devquest.ai.stub

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.TechInterviewPort
import org.slf4j.LoggerFactory

/**
 * D-008 — 학습 클러스터(EKS 실습) 전용 `TechInterviewPort` 스텁.
 *
 * `ANTHROPIC_API_KEY`를 학습 클러스터에 넣지 않기로 한 결정(`infra/aws-eks/2-cluster/secrets.tf:111-118`,
 * *"학습 클러스터에 prod 크리덴셜을 넣지 않는다"*)의 결과다. Stage C e2e가 실제로 타는 AI 경로는
 * `daily-api` → ai-api `/internal/ai/tech-interview/{daily-question,explain-followup,evaluate}` 뿐이므로
 * [TechInterviewPort] 하나만 스텁한다.
 *
 * 🔴 **다른 17개 AI 포트는 스텁되지 않는다** — 여전히 실제 `ANTHROPIC_API_KEY`가 필요하다.
 * "AI가 전부 스텁된다"고 오해하지 말 것.
 *
 * 스텁 응답에는 `[STUB]` 표식을 항상 포함한다 — "통과했다고 믿게 만드는 검사"를 만들지 않기 위해
 * (Stage A 스모크 원칙). 이 클래스가 실제로 호출됐다는 사실도 로그로 남긴다.
 *
 * 이 클래스는 `client-ai`를 건드리지 않는다(롤백 불변식) — ai-api 자신이 소유한 별도 구현체다.
 * [com.devquest.ai.config.AiStubConfig]가 `devquest.ai.stub.tech-interview.enabled=true`일 때만
 * 이 빈을 `@Primary`로 등록해 `client-ai`의 `TechInterviewEvaluator`를 덮어쓴다.
 */
class TechInterviewStubEvaluator : TechInterviewPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun generateQuestions(techStack: String): TechInterviewResult {
        log.info("[STUB] TechInterviewStubEvaluator.generateQuestions 호출됨: techStack=$techStack")
        return TechInterviewResult(
            questions = listOf("[STUB] $techStack 학습용 연습 질문입니다."),
            overallScore = 0,
            feedback = "[STUB] 학습 클러스터 스텁 응답입니다 - 실제 AI 평가가 아닙니다.",
            passed = false,
            modelAnswer = "[STUB] 모범 답안 없음(스텁)",
        )
    }

    override fun evaluate(techStack: String, questions: List<String>, answers: List<String>): TechInterviewResult {
        log.info("[STUB] TechInterviewStubEvaluator.evaluate 호출됨: techStack=$techStack")
        return TechInterviewResult(
            questions = questions,
            overallScore = 70,
            feedback = "[STUB] 학습 클러스터 스텁 응답입니다 - 실제 AI 평가가 아닙니다.",
            passed = true,
            modelAnswer = "[STUB] 모범 답안 없음(스텁)",
        )
    }

    override fun generateDailyQuestion(techStack: String, recentQuestions: List<String>): String {
        log.info("[STUB] TechInterviewStubEvaluator.generateDailyQuestion 호출됨: techStack=$techStack")
        return "[STUB] 오늘의 질문($techStack) - 학습 클러스터 스텁 응답입니다."
    }

    override fun explainFollowup(
        question: String,
        answer: String,
        feedback: String,
        userQuestion: String,
        modelAnswer: String?,
    ): String {
        log.info("[STUB] TechInterviewStubEvaluator.explainFollowup 호출됨")
        return "[STUB] 추가 질문(\"$userQuestion\")에 대한 설명입니다 - 학습 클러스터 스텁 응답입니다."
    }
}
