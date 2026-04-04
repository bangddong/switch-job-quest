package com.devquest.core.domain

import com.devquest.core.domain.model.QuestHistory
import com.devquest.core.domain.model.QuestProgress
import com.devquest.core.domain.model.evaluation.*
import com.devquest.core.domain.port.*
import com.devquest.core.enums.QuestStatus
import com.devquest.core.support.error.CoreException
import com.devquest.core.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class AiCheckService(
    private val essayEvaluator: EssayEvaluatorPort,
    private val blogEvaluator: BlogEvaluatorPort,
    private val systemDesignEvaluator: SystemDesignEvaluatorPort,
    private val interviewEvaluator: InterviewEvaluatorPort,
    private val jdAnalysisEvaluator: JdAnalysisEvaluatorPort,
    private val resumeEvaluator: ResumeEvaluatorPort,
    private val companyFitEvaluator: CompanyFitEvaluatorPort,
    private val personalityEvaluator: PersonalityEvaluatorPort,
    private val skillAssessmentPort: SkillAssessmentPort,
    private val actClearReportPort: ActClearReportPort,
    private val progressPort: QuestProgressPort,
    private val historyPort: QuestHistoryPort,
    private val bossPackageEvaluator: BossPackageEvaluatorPort,
    private val journeyReportPort: JourneyReportPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${devquest.ai.pass-score:70}")
    private val passScore: Int = 70

    @Transactional
    fun checkSkillAssessment(userId: String, skills: List<String>, targetRole: String): SkillAssessmentResult {
        val result = skillAssessmentPort.evaluate(skills, targetRole)
        saveProgress(userId, "1-1", 1, result.score, true, 150)
        return result
    }

    @Transactional
    fun checkCareerEssay(
        userId: String, dissatisfactions: List<String>, goals: List<String>, fiveYearVision: String
    ): EssayCheckResult {
        val result = essayEvaluator.evaluate(dissatisfactions, goals, fiveYearVision)
        saveProgress(userId, "1-2", 1, result.score, result.passed, if (result.passed) (200 * result.score / 100) else 0)
        return result
    }

    @Transactional
    fun checkTechBlog(userId: String, questId: String, techTopic: String, title: String, content: String): AiEvaluationResult {
        val result = blogEvaluator.evaluate(techTopic, title, content)
        val earnedXp = if (result.passed) (600 * result.xpMultiplier).toInt() else 0
        saveProgress(userId, questId, 2, result.score, result.passed, earnedXp)
        return result
    }

    @Transactional
    fun checkSystemDesign(
        userId: String, questId: String, problemStatement: String, architectureDescription: String, considerations: List<String>
    ): AiEvaluationResult {
        val result = systemDesignEvaluator.evaluate(problemStatement, architectureDescription, considerations)
        saveProgress(userId, questId, 2, result.score, result.passed, if (result.passed) (500 * result.xpMultiplier).toInt() else 0)
        return result
    }

    @Transactional
    fun checkMockInterview(userId: String, questId: String, category: String, question: String, answer: String, questionId: String): InterviewEvaluationResult {
        val result = interviewEvaluator.evaluate(category, question, answer, questionId)
        saveProgress(userId, questId, 2, result.score, result.passed, if (result.passed) 800 else 0)
        return result
    }

    fun generateInterviewQuestions(categories: List<String>, count: Int): List<Map<String, String>> {
        return interviewEvaluator.generateQuestions(categories, count)
    }

    @Transactional
    fun analyzeJd(userId: String, companyName: String, jobDescription: String, userSkills: List<String>, userExperiences: List<String>): JdAnalysisResult {
        val result = jdAnalysisEvaluator.analyze(companyName, jobDescription, userSkills, userExperiences)
        saveProgress(userId, "3-2", 3, result.overallMatchScore, true, 350)
        return result
    }

    @Transactional
    fun checkResume(userId: String, targetCompany: String, targetJd: String, resumeContent: String): ResumeCheckResult {
        val result = resumeEvaluator.evaluate(targetCompany, targetJd, resumeContent)
        val passed = result.overallScore >= passScore
        saveProgress(userId, "4-1", 4, result.overallScore, passed, if (passed) 500 else 0)
        return result
    }

    @Transactional
    fun analyzeCompanyFit(userId: String, preferences: Map<String, String>, companies: List<CompanyInfo>): List<CompanyFitResult> {
        val result = companyFitEvaluator.analyze(preferences, companies)
        val maxScore = result.maxOfOrNull { it.fitScore } ?: 0
        val passed = result.isNotEmpty() && maxScore >= passScore
        saveProgress(userId, "1-BOSS", 1, maxScore, passed, if (passed) 500 else 0)
        return result
    }

    @Transactional
    fun checkPersonalityInterview(userId: String, question: String, answer: String): AiEvaluationResult {
        val result = personalityEvaluator.evaluate(question, answer)
        saveProgress(userId, "5-1", 5, result.score, result.passed, if (result.passed) (400 * result.xpMultiplier).toInt() else 0)
        return result
    }

    @Transactional
    fun checkBossPackage(userId: String, resumeContent: String, githubUrl: String, blogUrl: String, targetPosition: String): BossPackageResult {
        val result = bossPackageEvaluator.evaluate(resumeContent, githubUrl, blogUrl, targetPosition)
        val passed = result.overallScore >= passScore
        saveProgress(userId, "4-BOSS", 4, result.overallScore, passed, if (passed) 700 else 0)
        return result
    }

    fun generateJourneyReport(userId: String, companyName: String, targetPosition: String): JourneyReportResult {
        val allProgress = progressPort.findAllByUserId(userId)
        val questScores = allProgress
            .filter { it.aiScore > 0 }
            .associate { it.questId to it.aiScore }
        val totalXp = allProgress.sumOf { it.earnedXp }
        val completedQuestCount = allProgress.count { it.status == QuestStatus.COMPLETED }
        return journeyReportPort.generate(companyName, targetPosition, questScores, totalXp, completedQuestCount)
    }

    fun generateActClearReport(userId: String, actId: Int, actTitle: String): ActClearReportResult {
        val questScores = progressPort.findAllByUserId(userId)
            .filter { it.actId == actId && it.aiScore > 0 }
            .associate { it.questId to it.aiScore }
        return actClearReportPort.generate(actId, actTitle, questScores)
    }

    private fun saveProgress(userId: String, questId: String, actId: Int, score: Int, passed: Boolean, xp: Int) {
        val existing = progressPort.findByUserIdAndQuestId(userId, questId)
        val progress = QuestProgress(
            id = existing?.id,
            userId = userId,
            questId = questId,
            actId = actId,
            status = if (passed) QuestStatus.COMPLETED else QuestStatus.AI_FAILED,
            aiScore = score,
            earnedXp = xp,
            completedAt = if (passed) LocalDateTime.now() else null,
            updatedAt = LocalDateTime.now()
        )
        progressPort.save(progress)
        saveHistory(userId, questId, actId, score, passed, xp)
        log.info("Quest progress saved: userId=$userId, questId=$questId, score=$score, passed=$passed, xp=$xp")
    }

    private fun saveHistory(userId: String, questId: String, actId: Int, score: Int, passed: Boolean, xp: Int) {
        val grade = when {
            score >= 90 -> "S"
            score >= 80 -> "A"
            score >= 70 -> "B"
            score >= 60 -> "C"
            else -> "D"
        }
        val history = QuestHistory(
            userId = userId,
            questId = questId,
            actId = actId,
            score = score,
            grade = grade,
            passed = passed,
            earnedXp = xp
        )
        historyPort.save(history)
        log.info("Quest history saved: userId=$userId, questId=$questId, score=$score, grade=$grade, passed=$passed")
    }
}
