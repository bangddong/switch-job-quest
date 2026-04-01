package com.devquest.core.domain

import com.devquest.core.domain.model.QuestHistory
import com.devquest.core.domain.model.evaluation.*
import com.devquest.core.domain.port.*
import com.devquest.core.enums.QuestStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class AiCheckServiceTest {

    @Mock lateinit var essayEvaluator: EssayEvaluatorPort
    @Mock lateinit var blogEvaluator: BlogEvaluatorPort
    @Mock lateinit var systemDesignEvaluator: SystemDesignEvaluatorPort
    @Mock lateinit var interviewEvaluator: InterviewEvaluatorPort
    @Mock lateinit var jdAnalysisEvaluator: JdAnalysisEvaluatorPort
    @Mock lateinit var resumeEvaluator: ResumeEvaluatorPort
    @Mock lateinit var companyFitEvaluator: CompanyFitEvaluatorPort
    @Mock lateinit var personalityEvaluator: PersonalityEvaluatorPort
    @Mock lateinit var skillAssessmentPort: SkillAssessmentPort
    @Mock lateinit var actClearReportPort: ActClearReportPort
    @Mock lateinit var progressPort: QuestProgressPort
    @Mock lateinit var historyPort: QuestHistoryPort

    private lateinit var service: AiCheckService

    @BeforeEach
    fun setUp() {
        service = AiCheckService(
            essayEvaluator, blogEvaluator, systemDesignEvaluator, interviewEvaluator,
            jdAnalysisEvaluator, resumeEvaluator, companyFitEvaluator, personalityEvaluator,
            skillAssessmentPort, actClearReportPort, progressPort, historyPort
        )
        // progressPort.save 기본 응답 (저장 시 반환값 필요)
        whenever(progressPort.save(any())).thenAnswer { it.arguments[0] }
        // historyPort.save 기본 응답
        whenever(historyPort.save(any())).thenAnswer { it.arguments[0] }
    }

    // ===== analyzeCompanyFit 테스트 =====

    @Test
    fun `analyzeCompanyFit - 빈 결과 반환 시 passed=false, xp=0으로 저장`() {
        whenever(companyFitEvaluator.analyze(any(), any())).thenReturn(emptyList())

        service.analyzeCompanyFit("user1", emptyMap(), emptyList())

        val captor = argumentCaptor<com.devquest.core.domain.model.QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.AI_FAILED)
        assertThat(captor.firstValue.earnedXp).isEqualTo(0)
        assertThat(captor.firstValue.aiScore).isEqualTo(0)
    }

    @Test
    fun `analyzeCompanyFit - 최고 점수가 69점이면 passed=false, xp=0으로 저장`() {
        val results = listOf(
            CompanyFitResult(companyName = "A사", fitScore = 69),
            CompanyFitResult(companyName = "B사", fitScore = 50)
        )
        whenever(companyFitEvaluator.analyze(any(), any())).thenReturn(results)

        service.analyzeCompanyFit("user1", emptyMap(), emptyList())

        val captor = argumentCaptor<com.devquest.core.domain.model.QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.AI_FAILED)
        assertThat(captor.firstValue.earnedXp).isEqualTo(0)
        assertThat(captor.firstValue.aiScore).isEqualTo(69)
    }

    @Test
    fun `analyzeCompanyFit - 최고 점수가 70점이면 passed=true, xp=500으로 저장`() {
        val results = listOf(
            CompanyFitResult(companyName = "A사", fitScore = 70),
            CompanyFitResult(companyName = "B사", fitScore = 60)
        )
        whenever(companyFitEvaluator.analyze(any(), any())).thenReturn(results)

        service.analyzeCompanyFit("user1", emptyMap(), emptyList())

        val captor = argumentCaptor<com.devquest.core.domain.model.QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.COMPLETED)
        assertThat(captor.firstValue.earnedXp).isEqualTo(500)
        assertThat(captor.firstValue.aiScore).isEqualTo(70)
    }

    @Test
    fun `analyzeCompanyFit - 여러 회사 중 하나라도 70 이상이면 passed=true`() {
        val results = listOf(
            CompanyFitResult(companyName = "A사", fitScore = 40),
            CompanyFitResult(companyName = "B사", fitScore = 95)
        )
        whenever(companyFitEvaluator.analyze(any(), any())).thenReturn(results)

        service.analyzeCompanyFit("user1", emptyMap(), emptyList())

        val captor = argumentCaptor<com.devquest.core.domain.model.QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.COMPLETED)
        assertThat(captor.firstValue.aiScore).isEqualTo(95)
        assertThat(captor.firstValue.questId).isEqualTo("1-BOSS")
    }

    // ===== checkResume 테스트 =====

    @Test
    fun `checkResume - 점수 70 이상이면 passed=true, xp=500으로 저장`() {
        whenever(resumeEvaluator.evaluate(any(), any(), any()))
            .thenReturn(ResumeCheckResult(overallScore = 85))

        service.checkResume("user1", "카카오", "JD", "이력서 내용")

        val captor = argumentCaptor<com.devquest.core.domain.model.QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.COMPLETED)
        assertThat(captor.firstValue.earnedXp).isEqualTo(500)
        assertThat(captor.firstValue.aiScore).isEqualTo(85)
        assertThat(captor.firstValue.questId).isEqualTo("4-1")
    }

    @Test
    fun `checkResume - 점수 69 이하면 passed=false, xp=0으로 저장`() {
        whenever(resumeEvaluator.evaluate(any(), any(), any()))
            .thenReturn(ResumeCheckResult(overallScore = 69))

        service.checkResume("user1", "카카오", "JD", "이력서 내용")

        val captor = argumentCaptor<com.devquest.core.domain.model.QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.AI_FAILED)
        assertThat(captor.firstValue.earnedXp).isEqualTo(0)
        assertThat(captor.firstValue.aiScore).isEqualTo(69)
    }

    // ===== checkCareerEssay 테스트 =====

    @Test
    fun `checkCareerEssay - passed=true면 XP = 200 * score 100으로 저장`() {
        whenever(essayEvaluator.evaluate(any(), any(), any()))
            .thenReturn(EssayCheckResult(score = 80, passed = true))

        service.checkCareerEssay("user1", listOf("불만"), listOf("목표"), "비전")

        val captor = argumentCaptor<com.devquest.core.domain.model.QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.COMPLETED)
        assertThat(captor.firstValue.earnedXp).isEqualTo(160) // 200 * 80 / 100
        assertThat(captor.firstValue.questId).isEqualTo("1-2")
    }

    @Test
    fun `checkCareerEssay - passed=false면 xp=0으로 저장`() {
        whenever(essayEvaluator.evaluate(any(), any(), any()))
            .thenReturn(EssayCheckResult(score = 50, passed = false))

        service.checkCareerEssay("user1", listOf("불만"), listOf("목표"), "비전")

        val captor = argumentCaptor<com.devquest.core.domain.model.QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.AI_FAILED)
        assertThat(captor.firstValue.earnedXp).isEqualTo(0)
    }

    // ===== checkTechBlog 테스트 =====

    @Test
    fun `checkTechBlog - passed=true면 XP = 600 * xpMultiplier으로 저장`() {
        whenever(blogEvaluator.evaluate(any(), any(), any()))
            .thenReturn(AiEvaluationResult(score = 85, passed = true, xpMultiplier = 1.5))

        service.checkTechBlog("user1", "2-1", "Kotlin", "제목", "내용")

        val captor = argumentCaptor<com.devquest.core.domain.model.QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.COMPLETED)
        assertThat(captor.firstValue.earnedXp).isEqualTo(900) // (600 * 1.5).toInt()
        assertThat(captor.firstValue.questId).isEqualTo("2-1")
    }

    @Test
    fun `checkTechBlog - passed=false면 xp=0으로 저장`() {
        whenever(blogEvaluator.evaluate(any(), any(), any()))
            .thenReturn(AiEvaluationResult(score = 40, passed = false, xpMultiplier = 1.0))

        service.checkTechBlog("user1", "2-1", "Kotlin", "제목", "내용")

        val captor = argumentCaptor<com.devquest.core.domain.model.QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.AI_FAILED)
        assertThat(captor.firstValue.earnedXp).isEqualTo(0)
    }

    // ===== checkMockInterview 테스트 =====

    @Test
    fun `checkMockInterview - passed=true면 xp=800 고정으로 저장`() {
        whenever(interviewEvaluator.evaluate(any(), any(), any(), any()))
            .thenReturn(InterviewEvaluationResult(score = 90, passed = true))

        service.checkMockInterview("user1", "2-2", "backend", "질문", "답변", "q-1")

        val captor = argumentCaptor<com.devquest.core.domain.model.QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.COMPLETED)
        assertThat(captor.firstValue.earnedXp).isEqualTo(800)
    }

    @Test
    fun `checkMockInterview - passed=false면 xp=0으로 저장`() {
        whenever(interviewEvaluator.evaluate(any(), any(), any(), any()))
            .thenReturn(InterviewEvaluationResult(score = 30, passed = false))

        service.checkMockInterview("user1", "2-2", "backend", "질문", "답변", "q-1")

        val captor = argumentCaptor<com.devquest.core.domain.model.QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.AI_FAILED)
        assertThat(captor.firstValue.earnedXp).isEqualTo(0)
    }

    // ===== analyzeJd 테스트 =====

    @Test
    fun `analyzeJd - 항상 passed=true이고 xp=350 고정으로 저장`() {
        whenever(jdAnalysisEvaluator.analyze(any(), any(), any(), any()))
            .thenReturn(JdAnalysisResult(companyName = "카카오", overallMatchScore = 60))

        service.analyzeJd("user1", "카카오", "JD 내용", listOf("Kotlin"), listOf("3년 경력"))

        val captor = argumentCaptor<com.devquest.core.domain.model.QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.COMPLETED)
        assertThat(captor.firstValue.earnedXp).isEqualTo(350)
        assertThat(captor.firstValue.questId).isEqualTo("3-2")
    }

    // ===== checkPersonalityInterview 테스트 =====

    @Test
    fun `checkPersonalityInterview - passed=true면 XP = 400 * xpMultiplier으로 저장`() {
        whenever(personalityEvaluator.evaluate(any(), any()))
            .thenReturn(AiEvaluationResult(score = 88, passed = true, xpMultiplier = 1.2))

        service.checkPersonalityInterview("user1", "장단점은?", "저는...")

        val captor = argumentCaptor<com.devquest.core.domain.model.QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.COMPLETED)
        assertThat(captor.firstValue.earnedXp).isEqualTo(480) // (400 * 1.2).toInt()
        assertThat(captor.firstValue.questId).isEqualTo("5-1")
    }
}
