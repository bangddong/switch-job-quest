package com.devquest.client.ai.integration

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.devquest.client.ai.evaluator.*
import com.devquest.client.ai.support.AiCallExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient

/**
 * 실제 Anthropic API를 호출하는 통합 테스트.
 *
 * 실행: ./gradlew :clients:client-ai:integrationTest
 * 환경변수 ANTHROPIC_API_KEY 필요. 없으면 전체 스킵.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiIntegrationTest {

    private lateinit var chatClient: ChatClient
    private lateinit var executor: AiCallExecutor

    @BeforeAll
    fun setup() {
        val apiKey = System.getenv("ANTHROPIC_API_KEY")
        assumeTrue(apiKey != null && apiKey.isNotBlank(), "ANTHROPIC_API_KEY 없음 — 스킵")

        val anthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        val options = AnthropicChatOptions.builder()
            .model("claude-haiku-4-5")
            .maxTokens(3000)
            .temperature(0.3)
            .build()
        val model = AnthropicChatModel.builder()
            .anthropicClient(anthropicClient)
            .options(options)
            .build()

        chatClient = ChatClient.builder(model).build()
        executor = AiCallExecutor(maxRetry = 2)
    }

    // ── CareerEssayEvaluator ───────────────────────────────────────────────

    @Test
    fun `자기소개서 평가 — 점수 범위와 JSON 파싱 검증`() {
        val evaluator = CareerEssayEvaluator(chatClient, executor)

        val result = evaluator.evaluate(
            dissatisfactions = listOf(
                "반복적인 유지보수 업무가 많아 기술 성장이 더딤",
                "의사결정 과정에서 개발자 의견이 반영되지 않음",
                "레거시 시스템으로 새 기술 적용 기회가 없음"
            ),
            goals = listOf(
                "AI/ML 기반 서비스 개발 경험 확보",
                "헥사고날 아키텍처 실무 적용",
                "기술 주도 문화의 팀에서 성장"
            ),
            fiveYearVision = "AI 플랫폼 팀 테크 리드로서 AI 서비스 안정성과 비용 최적화를 주도하고 싶습니다."
        )

        println("\n[CareerEssay] score=${result.score}, grade=${result.grade}, passed=${result.passed}")
        println("  clarity=${result.clarityScore}, logic=${result.logicScore}, motivation=${result.motivationScore}, growth=${result.growthScore}")
        println("  developerType=${result.developerType}")
        println("  feedback=${result.feedback.take(100)}...")

        assertThat(result.score).isBetween(0, 100)
        assertThat(result.grade).isNotBlank()
        assertThat(result.clarityScore + result.logicScore + result.motivationScore + result.growthScore)
            .isEqualTo(result.score)
        assertThat(result.feedback).isNotBlank()
        assertThat(result.developerType).isNotBlank()
    }

    // ── SkillAssessmentEvaluator ───────────────────────────────────────────

    @Test
    fun `기술 스택 평가 — 5년차 백엔드 개발자 시나리오`() {
        val evaluator = SkillAssessmentEvaluator(chatClient, executor)

        val result = evaluator.evaluate(
            skills = listOf(
                "Java:5년",
                "Spring Boot:4년",
                "MySQL:5년",
                "Kotlin:1년",
                "Docker:2년",
                "GitHub Actions:1년"
            ),
            targetRole = "시니어 백엔드 개발자 / AI 플랫폼 DevOps Engineer"
        )

        println("\n[SkillAssessment] score=${result.score}, grade=${result.grade}, passed=${result.passed}")
        println("  developerType=${result.developerType}")
        println("  strengths=${result.strengths}")
        println("  improvements=${result.improvements.take(2)}")

        assertThat(result.score).isBetween(0, 100)
        assertThat(result.developerType).isNotBlank()
        assertThat(result.strengths).isNotEmpty()
        assertThat(result.improvements).isNotEmpty()
        assertThat(result.feedback).isNotBlank()
    }

    // ── ResumeCheckEvaluator ───────────────────────────────────────────────

    @Test
    fun `이력서 평가 — STAR 기법 및 수치화 검증`() {
        val evaluator = ResumeCheckEvaluator(chatClient, executor)

        val resumeContent = """
            ## 경력사항

            ### (주)테크코어 / 백엔드 개발자 / 2020.03 ~ 현재 (4년)
            - 결제 API 개발 및 유지보수
            - 레거시 시스템 리팩토링 진행
            - 신규 서비스 백엔드 설계
            - 팀 코드 리뷰 문화 도입

            ### 기술 스택
            Java, Spring Boot, MySQL, Redis, Docker
        """.trimIndent()

        val result = evaluator.evaluate(
            targetCompany = "마이다스인",
            targetJd = "AI 플랫폼 DevOps Engineer - LLM 비용/성능/품질 최적화, K8s/GitOps 운영",
            resumeContent = resumeContent
        )

        println("\n[ResumeCheck] score=${result.overallScore}, passed=${result.passed}")
        println("  star=${result.starMethodScore}, quant=${result.quantificationScore}, keyword=${result.keywordMatchScore}")
        println("  improvements=${result.improvements.size}건, rewrites=${result.rewrittenExamples.size}건")
        if (result.improvements.isNotEmpty()) {
            println("  첫 번째 개선사항: ${result.improvements.first().suggestion}")
        }

        assertThat(result.overallScore).isBetween(0, 100)
        assertThat(result.starMethodScore + result.quantificationScore + result.keywordMatchScore)
            .isEqualTo(result.overallScore)
        assertThat(result.improvements).isNotEmpty()
    }

    // ── JdAnalysisEvaluator ────────────────────────────────────────────────

    @Test
    fun `JD 분석 — 필수 스킬 추출 및 매칭 점수 검증`() {
        val evaluator = JdAnalysisEvaluator(chatClient, executor)

        val result = evaluator.analyze(
            companyName = "마이다스인",
            jobDescription = """
                AI Platform DevOps Engineer
                - LLM 비용/성능/품질 통합 최적화
                - K8s 기반 AI Agent 플랫폼 운영
                - GitOps(ArgoCD/Flux) 기반 배포 파이프라인 구축
                - Prometheus/Grafana 관측성 파이프라인 운영
                - 우대: 오픈소스 LLM 운영 경험, AI Gateway 구축 경험
            """.trimIndent(),
            userSkills = listOf("Java", "Spring Boot", "Docker", "GitHub Actions", "Prometheus"),
            userExperiences = listOf(
                "결제 시스템 백엔드 개발 4년",
                "CI/CD 파이프라인 구축 (Jenkins → GitHub Actions 마이그레이션)",
                "AI 평가 시스템 설계 및 운영 (DevQuest 사이드 프로젝트)"
            )
        )

        println("\n[JdAnalysis] matchScore=${result.overallMatchScore}, passed=${result.passed}")
        println("  company=${result.companyName}")
        println("  requiredSkills=${result.requiredSkills.size}개")
        println("  hiddenRequirements=${result.hiddenRequirements.take(2)}")
        println("  strategy=${result.applicationStrategy.take(100)}...")

        assertThat(result.overallMatchScore).isBetween(0, 100)
        assertThat(result.requiredSkills).isNotEmpty()
        assertThat(result.applicationStrategy).isNotBlank()
    }

    // ── MockInterviewEvaluator ─────────────────────────────────────────────

    @Test
    fun `모의 면접 평가 — 5년차 기술 질문 채점`() {
        val evaluator = MockInterviewEvaluator(chatClient, executor)

        val result = evaluator.evaluate(
            category = "시스템 설계",
            question = "헥사고날 아키텍처(Port & Adapter)를 선택한 이유와 실제 적용 경험을 설명해주세요.",
            answer = """
                AI 서비스 개발에서 AI 제공자를 교체 가능하게 만들고 싶었습니다.
                EssayEvaluatorPort 같은 인터페이스를 도메인에 두고, 실제 Claude 호출은 어댑터로 분리했습니다.
                덕분에 테스트 시 모킹이 쉬워졌고, 나중에 다른 모델로 교체도 가능한 구조가 됐습니다.
                다만 어댑터 간 직접 의존을 금지하는 규칙을 팀 내에 정착시키는 게 쉽지 않았습니다.
            """.trimIndent(),
            questionId = "q-sysdesign-1",
            techStack = listOf("Kotlin", "Spring Boot", "Hexagonal Architecture"),
            yearsOfExperience = "3-5년"
        )

        println("\n[MockInterview] score=${result.score}, passed=${result.passed}")
        println("  accuracy=${result.technicalAccuracy}, depth=${result.depthAndApplication}")
        println("  practical=${result.practicalExperience}, comm=${result.communicationClarity}")
        println("  keyPointsMissed=${result.keyPointsMissed}")
        println("  improvements=${result.improvements.take(100)}...")

        assertThat(result.score).isBetween(0, 100)
        assertThat(result.technicalAccuracy + result.depthAndApplication +
                result.practicalExperience + result.communicationClarity)
            .isEqualTo(result.score)
        assertThat(result.correctAnswer).isNotBlank()
    }
}
