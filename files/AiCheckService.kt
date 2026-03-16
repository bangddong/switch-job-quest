package com.devquest.service

import com.devquest.ai.evaluator.*
import com.devquest.model.entity.QuestProgress
import com.devquest.model.entity.QuestStatus
import com.devquest.model.request.*
import com.devquest.model.response.*
import com.devquest.repository.QuestProgressRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val log = LoggerFactory.getLogger(AiCheckService::class.java)

@Service
class AiCheckService(
    private val essayEvaluator: CareerEssayEvaluator,
    private val blogEvaluator: TechBlogEvaluator,
    private val systemDesignEvaluator: SystemDesignEvaluator,
    private val interviewEvaluator: MockInterviewEvaluator,
    private val jdAnalysisEvaluator: JdAnalysisEvaluator,
    private val resumeCheckEvaluator: ResumeCheckEvaluator,
    private val companyFitEvaluator: CompanyFitEvaluator,
    private val personalityEvaluator: PersonalityInterviewEvaluator,
    private val progressRepository: QuestProgressRepository,
    private val objectMapper: ObjectMapper
) {
    @Value("\${devquest.ai.pass-score:70}")
    private val passScore: Int = 70

    // ── 이직 동기 에세이 검사 (Quest 1-2) ──────────────────────
    @Transactional
    fun checkCareerEssay(request: CareerEssayCheckRequest): ApiResponse<EssayCheckResult> {
        return runSafe("1-2", request.userId) {
            val result = essayEvaluator.evaluate(request)
            saveProgress(
                userId = request.userId,
                questId = "1-2",
                actId = 1,
                score = result.score,
                passed = result.passed,
                xp = if (result.passed) (200 * result.score / 100) else 0,
                evaluationJson = objectMapper.writeValueAsString(result)
            )
            result
        }
    }

    // ── 기술 블로그 검사 (Quest 2-2) ───────────────────────────
    @Transactional
    fun checkTechBlog(request: TechBlogCheckRequest): ApiResponse<AiEvaluationResult> {
        return runSafe(request.questId, request.userId) {
            val result = blogEvaluator.evaluate(request)
            val baseXp = 600
            val earnedXp = if (result.passed) (baseXp * result.xpMultiplier).toInt() else 0
            saveProgress(
                userId = request.userId,
                questId = request.questId,
                actId = 2,
                score = result.score,
                passed = result.passed,
                xp = earnedXp,
                evaluationJson = objectMapper.writeValueAsString(result)
            )
            result
        }
    }

    // ── 시스템 설계 평가 (Quest 2-3) ───────────────────────────
    @Transactional
    fun checkSystemDesign(request: SystemDesignCheckRequest): ApiResponse<AiEvaluationResult> {
        return runSafe(request.questId, request.userId) {
            val result = systemDesignEvaluator.evaluate(request)
            saveProgress(
                userId = request.userId,
                questId = request.questId,
                actId = 2,
                score = result.score,
                passed = result.passed,
                xp = if (result.passed) (500 * result.xpMultiplier).toInt() else 0,
                evaluationJson = objectMapper.writeValueAsString(result)
            )
            result
        }
    }

    // ── 모의 기술 면접 (Boss Quest 2-BOSS) ─────────────────────
    @Transactional
    fun checkMockInterview(request: MockInterviewRequest): ApiResponse<InterviewEvaluationResult> {
        return runSafe(request.questId, request.userId) {
            val result = interviewEvaluator.evaluate(request)
            saveProgress(
                userId = request.userId,
                questId = request.questId,
                actId = 2,
                score = result.score,
                passed = result.passed,
                xp = if (result.passed) 800 else 0,
                evaluationJson = objectMapper.writeValueAsString(result)
            )
            result
        }
    }

    // ── 면접 질문 생성 ──────────────────────────────────────────
    fun generateInterviewQuestions(
        categories: List<String> = listOf("DB", "JVM", "네트워크", "운영체제", "설계"),
        count: Int = 10
    ): ApiResponse<List<Map<String, String>>> {
        return runSafe("question-gen", "system") {
            interviewEvaluator.generateQuestions(categories, count)
        }
    }

    // ── JD 분석 (Quest 3-2) ─────────────────────────────────────
    @Transactional
    fun analyzeJd(request: JdAnalysisRequest): ApiResponse<JdAnalysisResult> {
        return runSafe("3-2", request.userId) {
            val result = jdAnalysisEvaluator.analyze(request)
            saveProgress(
                userId = request.userId,
                questId = "3-2",
                actId = 3,
                score = result.overallMatchScore,
                passed = true, // JD 분석은 점수와 무관하게 완료
                xp = 350,
                evaluationJson = objectMapper.writeValueAsString(result)
            )
            result
        }
    }

    // ── 이력서 검토 (Quest 4-1) ─────────────────────────────────
    @Transactional
    fun checkResume(request: ResumeCheckRequest): ApiResponse<ResumeCheckResult> {
        return runSafe("4-1", request.userId) {
            val result = resumeCheckEvaluator.evaluate(request)
            saveProgress(
                userId = request.userId,
                questId = "4-1",
                actId = 4,
                score = result.overallScore,
                passed = result.overallScore >= passScore,
                xp = if (result.overallScore >= passScore) 500 else 0,
                evaluationJson = objectMapper.writeValueAsString(result)
            )
            result
        }
    }

    // ── 회사 핏 분석 (Boss Quest 1-BOSS, 3-BOSS) ───────────────
    @Transactional
    fun analyzeCompanyFit(request: CompanyFitRequest): ApiResponse<List<CompanyFitResult>> {
        return runSafe("fit-analysis", request.userId) {
            val result = companyFitEvaluator.analyze(request)
            saveProgress(
                userId = request.userId,
                questId = "1-BOSS",
                actId = 1,
                score = result.maxOfOrNull { it.fitScore } ?: 0,
                passed = true,
                xp = 500,
                evaluationJson = objectMapper.writeValueAsString(result)
            )
            result
        }
    }

    // ── 인성 면접 평가 (Quest 5-1) ─────────────────────────────
    @Transactional
    fun checkPersonalityInterview(request: PersonalityInterviewRequest): ApiResponse<AiEvaluationResult> {
        return runSafe("5-1", request.userId) {
            val result = personalityEvaluator.evaluate(request)
            saveProgress(
                userId = request.userId,
                questId = "5-1",
                actId = 5,
                score = result.score,
                passed = result.passed,
                xp = if (result.passed) (400 * result.xpMultiplier).toInt() else 0,
                evaluationJson = objectMapper.writeValueAsString(result)
            )
            result
        }
    }

    // ── Progress 조회 ────────────────────────────────────────────
    fun getProgress(userId: String): ApiResponse<Map<String, Any>> {
        val progresses = progressRepository.findAllByUserId(userId)
        val totalXp = progresses.filter { it.status == QuestStatus.COMPLETED }.sumOf { it.earnedXp }
        val completedQuests = progresses.filter { it.status == QuestStatus.COMPLETED }.map { it.questId }

        return ApiResponse.ok(mapOf(
            "userId" to userId,
            "totalXp" to totalXp,
            "completedQuests" to completedQuests,
            "level" to (totalXp / 500 + 1),
            "questDetails" to progresses.associate { it.questId to mapOf(
                "status" to it.status,
                "score" to it.aiScore,
                "xp" to it.earnedXp
            )}
        ))
    }

    // ── 내부 유틸 ────────────────────────────────────────────────
    private fun saveProgress(
        userId: String, questId: String, actId: Int,
        score: Int, passed: Boolean, xp: Int, evaluationJson: String
    ) {
        val existing = progressRepository.findByUserIdAndQuestId(userId, questId)
        val progress = (existing ?: QuestProgress(userId = userId, questId = questId, actId = actId)).copy(
            status = if (passed) QuestStatus.COMPLETED else QuestStatus.AI_FAILED,
            aiScore = score,
            earnedXp = xp,
            aiEvaluationJson = evaluationJson,
            completedAt = if (passed) LocalDateTime.now() else null,
            updatedAt = LocalDateTime.now()
        )
        progressRepository.save(progress)
        log.info("Quest progress saved: userId=$userId, questId=$questId, score=$score, passed=$passed, xp=$xp")
    }

    private fun <T> runSafe(questId: String, userId: String, block: () -> T): ApiResponse<T> {
        return try {
            ApiResponse.ok(block(), "AI 평가 완료")
        } catch (e: Exception) {
            log.error("AI 평가 실패: questId=$questId, userId=$userId, error=${e.message}", e)
            ApiResponse.fail("AI 평가 중 오류가 발생했습니다: ${e.message}")
        }
    }
}
