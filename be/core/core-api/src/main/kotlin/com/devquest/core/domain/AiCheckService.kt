package com.devquest.core.domain

import com.devquest.core.domain.model.evaluation.*
import com.devquest.core.domain.port.*
import com.devquest.core.domain.PassCriteriaPolicy
import com.devquest.core.domain.QuestConstants
import com.devquest.core.domain.QuestXpPolicy
import com.devquest.core.enums.QuestStatus
import tools.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

/**
 * ⚠️ **트랜잭션 경계 (Phase 1 Task 1.4b/1.5)**: 이 클래스의 메서드는 의도적으로 `@Transactional`을
 * 붙이지 않는다. AI 호출이 HTTP 전송 계층(`devquest.ai.transport=http`)으로 바뀌면 네트워크 지연이
 * 수십 초까지 늘 수 있는데, 예전처럼 메서드 전체를 `@Transactional`로 감싸면 그 지연 내내 DB
 * 커넥션을 붙잡고 있어 HikariCP 풀 고갈 위험이 커진다(Task 1.4b 조사).
 *
 * 여기 모든 메서드는 "AI 호출 → [questProgressRecorder.record]에 위임"(또는
 * [evaluateDeveloperClass]처럼 "짧은 읽기 → AI 호출 → record 위임") 패턴이다. 원자성을 잃지 않는
 * 이유: [QuestProgressRecorder.record]가 진행상황 저장 + 히스토리 저장 + 이벤트 발행을 묶는 실제
 * 원자적 단위이고, 그 메서드 자체가 별도 빈(`@Component`)에서 `@Transactional`로 선언돼 있어
 * Spring AOP 프록시를 정상적으로 거친다(같은 클래스 안에서 `this.xxx()`로 호출하는 self-invocation
 * 함정과 무관 — `QuestProgressRecorder`는 처음부터 별도 주입 빈이었다). 이 클래스가 메서드 전체를
 * 감싸던 예전 `@Transactional`은 그 자체로 추가 원자성을 준 적이 없었다 — 각 메서드가 만드는 DB
 * 부수효과는 `record()` 호출 단 한 번뿐이라, 바깥 트랜잭션은 "AI 호출 동안 커넥션을 붙잡아 두는"
 * 부작용만 있었다. 제거해도 잃는 보장은 없다(상세 근거는 이관 계획 문서 Task 1.4b/1.5 절 참고).
 */
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
    private val developerClassEvaluator: DeveloperClassEvaluatorPort,
    private val objectMapper: ObjectMapper,
) {
    fun checkSkillAssessment(userId: String, skills: List<String>, targetRole: String): SkillAssessmentResult {
        val result = skillAssessmentPort.evaluate(skills, targetRole)
        val json = objectMapper.writeValueAsString(result)
        questProgressRecorder.record(userId, QuestConstants.SKILL_ASSESSMENT, 1, result.score, true, QuestXpPolicy.calculate(QuestConstants.SKILL_ASSESSMENT, true), json)
        return result
    }

    fun checkCareerEssay(
        userId: String, dissatisfactions: List<String>, goals: List<String>, fiveYearVision: String
    ): EssayCheckResult {
        val result = essayEvaluator.evaluate(dissatisfactions, goals, fiveYearVision)
        val json = objectMapper.writeValueAsString(result)
        questProgressRecorder.record(userId, QuestConstants.CAREER_ESSAY, 1, result.score, result.passed, QuestXpPolicy.calculate(QuestConstants.CAREER_ESSAY, result.passed, score = result.score), json)
        return result
    }

    fun checkTechBlog(userId: String, questId: String, techTopic: String, title: String, content: String): AiEvaluationResult {
        val result = blogEvaluator.evaluate(techTopic, title, content)
        questProgressRecorder.record(userId, questId, 2, result.score, result.passed, QuestXpPolicy.calculate(questId, result.passed, xpMultiplier = result.xpMultiplier))
        return result
    }

    fun checkSystemDesign(
        userId: String, questId: String, problemStatement: String, architectureDescription: String, considerations: List<String>
    ): AiEvaluationResult {
        val result = systemDesignEvaluator.evaluate(problemStatement, architectureDescription, considerations)
        questProgressRecorder.record(userId, questId, 2, result.score, result.passed, QuestXpPolicy.calculate(questId, result.passed, xpMultiplier = result.xpMultiplier))
        return result
    }

    fun checkMockInterview(
        userId: String,
        questId: String,
        category: String,
        question: String,
        answer: String,
        questionId: String,
        techStack: List<String>,
        yearsOfExperience: String
    ): InterviewEvaluationResult {
        val result = interviewEvaluator.evaluate(category, question, answer, questionId, techStack, yearsOfExperience)
        questProgressRecorder.record(userId, questId, 2, result.score, result.passed, QuestXpPolicy.calculate(questId, result.passed))
        return result
    }

    fun generateInterviewQuestions(
        techStack: List<String>,
        targetRole: String,
        yearsOfExperience: String,
        categories: List<String>,
        personalityCount: Int,
        techCount: Int
    ): List<Map<String, String>> {
        return interviewEvaluator.generateQuestions(techStack, targetRole, yearsOfExperience, categories, personalityCount, techCount)
    }

    fun analyzeJd(userId: String, companyName: String, jobDescription: String, userSkills: List<String>, userExperiences: List<String>): JdAnalysisResult {
        val result = jdAnalysisEvaluator.analyze(companyName, jobDescription, userSkills, userExperiences)
        val passed = PassCriteriaPolicy.evaluate(result.overallMatchScore)
        questProgressRecorder.record(userId, QuestConstants.JD_ANALYSIS, 3, result.overallMatchScore, passed, QuestXpPolicy.calculate(QuestConstants.JD_ANALYSIS, passed))
        return result
    }

    fun checkResume(userId: String, targetCompany: String, targetJd: String, resumeContent: String): ResumeCheckResult {
        val result = resumeEvaluator.evaluate(targetCompany, targetJd, resumeContent)
        val passed = PassCriteriaPolicy.evaluate(result.overallScore)
        questProgressRecorder.record(userId, QuestConstants.RESUME_CHECK, 4, result.overallScore, passed, QuestXpPolicy.calculate(QuestConstants.RESUME_CHECK, passed))
        return result
    }

    fun analyzeCompanyFit(userId: String, preferences: Map<String, String>, companies: List<CompanyInfo>): List<CompanyFitResult> {
        val result = companyFitEvaluator.analyze(preferences, companies)
        val maxScore = result.maxOfOrNull { it.fitScore } ?: 0
        val passed = PassCriteriaPolicy.evaluateMax(result.map { it.fitScore })
        questProgressRecorder.record(userId, QuestConstants.COMPANY_FIT_BOSS, 1, maxScore, passed, QuestXpPolicy.calculate(QuestConstants.COMPANY_FIT_BOSS, passed))
        return result
    }

    fun evaluateDeveloperClass(userId: String): DeveloperClassResult {
        val skillJson = progressPort.findByUserIdAndQuestId(userId, QuestConstants.SKILL_ASSESSMENT)?.aiEvaluationJson ?: ""
        val essayJson = progressPort.findByUserIdAndQuestId(userId, QuestConstants.CAREER_ESSAY)?.aiEvaluationJson ?: ""
        val result = developerClassEvaluator.evaluate(skillJson, essayJson)
        val passed = PassCriteriaPolicy.evaluate(result.overallScore)
        val normalizedResult = result.copy(passed = passed)
        questProgressRecorder.record(userId, QuestConstants.COMPANY_FIT_BOSS, 1, normalizedResult.overallScore, normalizedResult.passed, QuestXpPolicy.calculate(QuestConstants.COMPANY_FIT_BOSS, normalizedResult.passed), objectMapper.writeValueAsString(normalizedResult))
        return normalizedResult
    }

    fun checkPersonalityInterview(userId: String, question: String, answer: String): AiEvaluationResult {
        val result = personalityEvaluator.evaluate(question, answer)
        questProgressRecorder.record(userId, QuestConstants.PERSONALITY_INTERVIEW, 5, result.score, result.passed, QuestXpPolicy.calculate(QuestConstants.PERSONALITY_INTERVIEW, result.passed, xpMultiplier = result.xpMultiplier))
        return result
    }

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
