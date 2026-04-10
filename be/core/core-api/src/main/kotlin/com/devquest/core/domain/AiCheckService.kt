package com.devquest.core.domain

import com.devquest.core.domain.model.evaluation.*
import com.devquest.core.domain.port.*
import com.devquest.core.domain.PassCriteriaPolicy
import com.devquest.core.domain.QuestConstants
import com.devquest.core.domain.QuestXpPolicy
import com.devquest.core.enums.QuestStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
    private val bossPackageEvaluator: BossPackageEvaluatorPort,
    private val journeyReportPort: JourneyReportPort,
    private val questProgressRecorder: QuestProgressRecorder,
) {
    @Transactional
    fun checkSkillAssessment(userId: String, skills: List<String>, targetRole: String): SkillAssessmentResult {
        val result = skillAssessmentPort.evaluate(skills, targetRole)
        questProgressRecorder.record(userId, QuestConstants.SKILL_ASSESSMENT, 1, result.score, true, QuestXpPolicy.calculate(QuestConstants.SKILL_ASSESSMENT, true))
        return result
    }

    @Transactional
    fun checkCareerEssay(
        userId: String, dissatisfactions: List<String>, goals: List<String>, fiveYearVision: String
    ): EssayCheckResult {
        val result = essayEvaluator.evaluate(dissatisfactions, goals, fiveYearVision)
        questProgressRecorder.record(userId, QuestConstants.CAREER_ESSAY, 1, result.score, result.passed, QuestXpPolicy.calculate(QuestConstants.CAREER_ESSAY, result.passed, score = result.score))
        return result
    }

    @Transactional
    fun checkTechBlog(userId: String, questId: String, techTopic: String, title: String, content: String): AiEvaluationResult {
        val result = blogEvaluator.evaluate(techTopic, title, content)
        questProgressRecorder.record(userId, questId, 2, result.score, result.passed, QuestXpPolicy.calculate(questId, result.passed, xpMultiplier = result.xpMultiplier))
        return result
    }

    @Transactional
    fun checkSystemDesign(
        userId: String, questId: String, problemStatement: String, architectureDescription: String, considerations: List<String>
    ): AiEvaluationResult {
        val result = systemDesignEvaluator.evaluate(problemStatement, architectureDescription, considerations)
        questProgressRecorder.record(userId, questId, 2, result.score, result.passed, QuestXpPolicy.calculate(questId, result.passed, xpMultiplier = result.xpMultiplier))
        return result
    }

    @Transactional
    fun checkMockInterview(userId: String, questId: String, category: String, question: String, answer: String, questionId: String): InterviewEvaluationResult {
        val result = interviewEvaluator.evaluate(category, question, answer, questionId)
        questProgressRecorder.record(userId, questId, 2, result.score, result.passed, QuestXpPolicy.calculate(questId, result.passed))
        return result
    }

    fun generateInterviewQuestions(categories: List<String>, count: Int): List<Map<String, String>> {
        return interviewEvaluator.generateQuestions(categories, count)
    }

    @Transactional
    fun analyzeJd(userId: String, companyName: String, jobDescription: String, userSkills: List<String>, userExperiences: List<String>): JdAnalysisResult {
        val result = jdAnalysisEvaluator.analyze(companyName, jobDescription, userSkills, userExperiences)
        questProgressRecorder.record(userId, QuestConstants.JD_ANALYSIS, 3, result.overallMatchScore, true, QuestXpPolicy.calculate(QuestConstants.JD_ANALYSIS, true))
        return result
    }

    @Transactional
    fun checkResume(userId: String, targetCompany: String, targetJd: String, resumeContent: String): ResumeCheckResult {
        val result = resumeEvaluator.evaluate(targetCompany, targetJd, resumeContent)
        val passed = PassCriteriaPolicy.evaluate(result.overallScore)
        questProgressRecorder.record(userId, QuestConstants.RESUME_CHECK, 4, result.overallScore, passed, QuestXpPolicy.calculate(QuestConstants.RESUME_CHECK, passed))
        return result
    }

    @Transactional
    fun analyzeCompanyFit(userId: String, preferences: Map<String, String>, companies: List<CompanyInfo>): List<CompanyFitResult> {
        val result = companyFitEvaluator.analyze(preferences, companies)
        val maxScore = result.maxOfOrNull { it.fitScore } ?: 0
        val passed = PassCriteriaPolicy.evaluateMax(result.map { it.fitScore })
        questProgressRecorder.record(userId, QuestConstants.COMPANY_FIT_BOSS, 1, maxScore, passed, QuestXpPolicy.calculate(QuestConstants.COMPANY_FIT_BOSS, passed))
        return result
    }

    @Transactional
    fun checkPersonalityInterview(userId: String, question: String, answer: String): AiEvaluationResult {
        val result = personalityEvaluator.evaluate(question, answer)
        questProgressRecorder.record(userId, QuestConstants.PERSONALITY_INTERVIEW, 5, result.score, result.passed, QuestXpPolicy.calculate(QuestConstants.PERSONALITY_INTERVIEW, result.passed, xpMultiplier = result.xpMultiplier))
        return result
    }

    @Transactional
    fun checkBossPackage(userId: String, resumeContent: String, githubUrl: String, blogUrl: String, targetPosition: String): BossPackageResult {
        val result = bossPackageEvaluator.evaluate(resumeContent, githubUrl, blogUrl, targetPosition)
        val passed = PassCriteriaPolicy.evaluate(result.overallScore)
        questProgressRecorder.record(userId, QuestConstants.BOSS_PACKAGE, 4, result.overallScore, passed, QuestXpPolicy.calculate(QuestConstants.BOSS_PACKAGE, passed))
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
}
