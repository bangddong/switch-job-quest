package com.devquest.core.domain

import com.devquest.core.domain.model.evaluation.*
import com.devquest.core.domain.port.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
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
    @Mock lateinit var bossPackageEvaluator: BossPackageEvaluatorPort
    @Mock lateinit var journeyReportPort: JourneyReportPort
    @Mock lateinit var questProgressRecorder: QuestProgressRecorder

    @InjectMocks
    private lateinit var service: AiCheckService

    // ===== analyzeCompanyFit 테스트 =====

    @Test
    fun `analyzeCompanyFit - 빈 결과 반환 시 passed=false, xp=0으로 저장`() {
        whenever(companyFitEvaluator.analyze(any(), any())).thenReturn(emptyList())

        service.analyzeCompanyFit("user1", emptyMap(), emptyList())

        verify(questProgressRecorder).record(eq("user1"), eq("1-BOSS"), eq(1), eq(0), eq(false), eq(0))
    }

    @Test
    fun `analyzeCompanyFit - 최고 점수가 69점이면 passed=false, xp=0으로 저장`() {
        val results = listOf(
            CompanyFitResult(companyName = "A사", fitScore = 69),
            CompanyFitResult(companyName = "B사", fitScore = 50)
        )
        whenever(companyFitEvaluator.analyze(any(), any())).thenReturn(results)

        service.analyzeCompanyFit("user1", emptyMap(), emptyList())

        verify(questProgressRecorder).record(eq("user1"), eq("1-BOSS"), eq(1), eq(69), eq(false), eq(0))
    }

    @Test
    fun `analyzeCompanyFit - 최고 점수가 70점이면 passed=true, xp=500으로 저장`() {
        val results = listOf(
            CompanyFitResult(companyName = "A사", fitScore = 70),
            CompanyFitResult(companyName = "B사", fitScore = 60)
        )
        whenever(companyFitEvaluator.analyze(any(), any())).thenReturn(results)

        service.analyzeCompanyFit("user1", emptyMap(), emptyList())

        verify(questProgressRecorder).record(eq("user1"), eq("1-BOSS"), eq(1), eq(70), eq(true), eq(500))
    }

    @Test
    fun `analyzeCompanyFit - 여러 회사 중 하나라도 70 이상이면 passed=true`() {
        val results = listOf(
            CompanyFitResult(companyName = "A사", fitScore = 40),
            CompanyFitResult(companyName = "B사", fitScore = 95)
        )
        whenever(companyFitEvaluator.analyze(any(), any())).thenReturn(results)

        service.analyzeCompanyFit("user1", emptyMap(), emptyList())

        val userIdCaptor = argumentCaptor<String>()
        val questIdCaptor = argumentCaptor<String>()
        val actIdCaptor = argumentCaptor<Int>()
        val scoreCaptor = argumentCaptor<Int>()
        val passedCaptor = argumentCaptor<Boolean>()
        val xpCaptor = argumentCaptor<Int>()
        verify(questProgressRecorder).record(
            userIdCaptor.capture(), questIdCaptor.capture(), actIdCaptor.capture(),
            scoreCaptor.capture(), passedCaptor.capture(), xpCaptor.capture()
        )
        assertThat(questIdCaptor.firstValue).isEqualTo("1-BOSS")
        assertThat(scoreCaptor.firstValue).isEqualTo(95)
        assertThat(passedCaptor.firstValue).isTrue()
    }

    // ===== checkResume 테스트 =====

    @Test
    fun `checkResume - 점수 70 이상이면 passed=true, xp=500으로 저장`() {
        whenever(resumeEvaluator.evaluate(any(), any(), any()))
            .thenReturn(ResumeCheckResult(overallScore = 85))

        service.checkResume("user1", "카카오", "JD", "이력서 내용")

        verify(questProgressRecorder).record(eq("user1"), eq("4-1"), eq(4), eq(85), eq(true), eq(500))
    }

    @Test
    fun `checkResume - 점수 69 이하면 passed=false, xp=0으로 저장`() {
        whenever(resumeEvaluator.evaluate(any(), any(), any()))
            .thenReturn(ResumeCheckResult(overallScore = 69))

        service.checkResume("user1", "카카오", "JD", "이력서 내용")

        verify(questProgressRecorder).record(eq("user1"), eq("4-1"), eq(4), eq(69), eq(false), eq(0))
    }

    // ===== checkCareerEssay 테스트 =====

    @Test
    fun `checkCareerEssay - passed=true면 XP = 200 * score 100으로 저장`() {
        whenever(essayEvaluator.evaluate(any(), any(), any()))
            .thenReturn(EssayCheckResult(score = 80, passed = true))

        service.checkCareerEssay("user1", listOf("불만"), listOf("목표"), "비전")

        verify(questProgressRecorder).record(eq("user1"), eq("1-2"), eq(1), eq(80), eq(true), eq(160))
    }

    @Test
    fun `checkCareerEssay - passed=false면 xp=0으로 저장`() {
        whenever(essayEvaluator.evaluate(any(), any(), any()))
            .thenReturn(EssayCheckResult(score = 50, passed = false))

        service.checkCareerEssay("user1", listOf("불만"), listOf("목표"), "비전")

        verify(questProgressRecorder).record(eq("user1"), eq("1-2"), eq(1), eq(50), eq(false), eq(0))
    }

    // ===== checkTechBlog 테스트 =====

    @Test
    fun `checkTechBlog - passed=true면 XP = 600 * xpMultiplier으로 저장`() {
        whenever(blogEvaluator.evaluate(any(), any(), any()))
            .thenReturn(AiEvaluationResult(score = 85, passed = true, xpMultiplier = 1.5))

        service.checkTechBlog("user1", "2-1", "Kotlin", "제목", "내용")

        verify(questProgressRecorder).record(eq("user1"), eq("2-1"), eq(2), eq(85), eq(true), eq(900))
    }

    @Test
    fun `checkTechBlog - passed=false면 xp=0으로 저장`() {
        whenever(blogEvaluator.evaluate(any(), any(), any()))
            .thenReturn(AiEvaluationResult(score = 40, passed = false, xpMultiplier = 1.0))

        service.checkTechBlog("user1", "2-1", "Kotlin", "제목", "내용")

        verify(questProgressRecorder).record(eq("user1"), eq("2-1"), eq(2), eq(40), eq(false), eq(0))
    }

    // ===== checkMockInterview 테스트 =====

    @Test
    fun `checkMockInterview - passed=true면 xp=800 고정으로 저장`() {
        whenever(interviewEvaluator.evaluate(any(), any(), any(), any()))
            .thenReturn(InterviewEvaluationResult(score = 90, passed = true))

        service.checkMockInterview("user1", "2-2", "backend", "질문", "답변", "q-1")

        verify(questProgressRecorder).record(eq("user1"), eq("2-2"), eq(2), eq(90), eq(true), eq(800))
    }

    @Test
    fun `checkMockInterview - passed=false면 xp=0으로 저장`() {
        whenever(interviewEvaluator.evaluate(any(), any(), any(), any()))
            .thenReturn(InterviewEvaluationResult(score = 30, passed = false))

        service.checkMockInterview("user1", "2-2", "backend", "질문", "답변", "q-1")

        verify(questProgressRecorder).record(eq("user1"), eq("2-2"), eq(2), eq(30), eq(false), eq(0))
    }

    // ===== analyzeJd 테스트 =====

    @Test
    fun `analyzeJd - 항상 passed=true이고 xp=350 고정으로 저장`() {
        whenever(jdAnalysisEvaluator.analyze(any(), any(), any(), any()))
            .thenReturn(JdAnalysisResult(companyName = "카카오", overallMatchScore = 60))

        service.analyzeJd("user1", "카카오", "JD 내용", listOf("Kotlin"), listOf("3년 경력"))

        verify(questProgressRecorder).record(eq("user1"), eq("3-2"), eq(3), eq(60), eq(true), eq(350))
    }

    // ===== checkBossPackage 테스트 =====

    @Test
    fun `checkBossPackage - 70점 이상이면 COMPLETED로 저장한다`() {
        val mockResult = BossPackageResult(
            overallScore = 80,
            resumeImpactScore = 16,
            githubConsistencyScore = 16,
            technicalDepthScore = 16,
            positionFitScore = 16,
            differentiationScore = 16,
            strengths = listOf("강점1", "강점2"),
            improvements = listOf("개선사항1"),
            overallFeedback = "우수한 지원 패키지입니다."
        )
        whenever(bossPackageEvaluator.evaluate(any(), any(), any(), any())).thenReturn(mockResult)

        service.checkBossPackage("user-1", "이력서 내용", "https://github.com/user", "", "시니어 백엔드")

        verify(questProgressRecorder).record(eq("user-1"), eq("4-BOSS"), eq(4), eq(80), eq(true), eq(700))
    }

    // ===== checkPersonalityInterview 테스트 =====

    @Test
    fun `checkPersonalityInterview - passed=true면 XP = 400 * xpMultiplier으로 저장`() {
        whenever(personalityEvaluator.evaluate(any(), any()))
            .thenReturn(AiEvaluationResult(score = 88, passed = true, xpMultiplier = 1.2))

        service.checkPersonalityInterview("user1", "장단점은?", "저는...")

        verify(questProgressRecorder).record(eq("user1"), eq("5-1"), eq(5), eq(88), eq(true), eq(480))
    }
}
