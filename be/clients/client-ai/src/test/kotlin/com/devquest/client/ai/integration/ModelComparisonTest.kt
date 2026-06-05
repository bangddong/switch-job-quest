package com.devquest.client.ai.integration

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.devquest.client.ai.evaluator.CareerEssayEvaluator
import com.devquest.client.ai.evaluator.MockInterviewEvaluator
import com.devquest.client.ai.evaluator.SkillAssessmentEvaluator
import com.devquest.client.ai.support.AiCallExecutor
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.chat.client.ChatClient

/**
 * 동일 입력으로 Haiku vs Sonnet 결과를 나란히 출력하는 모델 비교 테스트.
 *
 * 실행: ./gradlew :clients:client-ai:integrationTest
 *
 * 목적:
 *  - 두 모델 간 점수 편차 확인
 *  - 피드백 품질 육안 비교
 *  - 모델 교체 시 회귀 기준 마련
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelComparisonTest {

    private lateinit var haikuClient: ChatClient
    private lateinit var sonnetClient: ChatClient
    private lateinit var executor: AiCallExecutor

    @BeforeAll
    fun setup() {
        val apiKey = System.getenv("ANTHROPIC_API_KEY")
        assumeTrue(apiKey != null && apiKey.isNotBlank(), "ANTHROPIC_API_KEY 없음 — 스킵")

        val anthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

        haikuClient = ChatClient.builder(
            AnthropicChatModel.builder()
                .anthropicClient(anthropicClient)
                .options(
                    AnthropicChatOptions.builder()
                        .model("claude-haiku-4-5")
                        .maxTokens(1500)
                        .temperature(0.3)
                        .build()
                )
                .build()
        ).build()

        sonnetClient = ChatClient.builder(
            AnthropicChatModel.builder()
                .anthropicClient(anthropicClient)
                .options(
                    AnthropicChatOptions.builder()
                        .model("claude-sonnet-4-6")
                        .maxTokens(2000)
                        .temperature(0.3)
                        .build()
                )
                .build()
        ).build()

        executor = AiCallExecutor(maxRetry = 1)
    }

    // ── 자기소개서 평가 비교 ───────────────────────────────────────────────

    @Test
    fun `모델 비교 - 자기소개서 평가 Haiku vs Sonnet`() {
        val dissatisfactions = listOf(
            "반복적인 유지보수 업무가 많아 기술 성장이 더딤",
            "레거시 시스템으로 새 기술 적용 기회가 없음"
        )
        val goals = listOf(
            "AI 서비스 안정성 및 비용 최적화 전문가로 성장",
            "K8s/GitOps 기반 인프라 역량 확보"
        )
        val vision = "5년 후 AI 플랫폼 팀 테크 리드로서 전사 AI 서비스의 신뢰성을 책임지고 싶습니다."

        val haiku = CareerEssayEvaluator(haikuClient, executor)
            .evaluate(dissatisfactions, goals, vision)
        val sonnet = CareerEssayEvaluator(sonnetClient, executor)
            .evaluate(dissatisfactions, goals, vision)

        println("\n" + "=".repeat(60))
        println("  자기소개서 평가: Haiku vs Sonnet")
        println("=".repeat(60))
        println("%-20s | %-10s | %-10s".format("항목", "Haiku", "Sonnet"))
        println("-".repeat(44))
        println("%-20s | %-10s | %-10s".format("총점", haiku.score, sonnet.score))
        println("%-20s | %-10s | %-10s".format("등급", haiku.grade, sonnet.grade))
        println("%-20s | %-10s | %-10s".format("통과", haiku.passed, sonnet.passed))
        println("%-20s | %-10s | %-10s".format("명확성(30)", haiku.clarityScore, sonnet.clarityScore))
        println("%-20s | %-10s | %-10s".format("논리성(30)", haiku.logicScore, sonnet.logicScore))
        println("%-20s | %-10s | %-10s".format("진정성(20)", haiku.motivationScore, sonnet.motivationScore))
        println("%-20s | %-10s | %-10s".format("성장방향(20)", haiku.growthScore, sonnet.growthScore))
        println("%-20s | %-10s | %-10s".format("점수 차이", "", Math.abs(haiku.score - sonnet.score)))
        println()
        println("[Haiku] developerType: ${haiku.developerType}")
        println("[Sonnet] developerType: ${sonnet.developerType}")
        println()
        println("[Haiku] feedback: ${haiku.feedback.take(120)}...")
        println("[Sonnet] feedback: ${sonnet.feedback.take(120)}...")
        println("=".repeat(60))
    }

    // ── 기술 스택 평가 비교 ───────────────────────────────────────────────

    @Test
    fun `모델 비교 - 기술 스택 평가 Haiku vs Sonnet`() {
        val skills = listOf(
            "Java:5년", "Spring Boot:4년", "MySQL:5년",
            "Kotlin:1년", "Docker:2년", "GitHub Actions:1년",
            "Prometheus:1년", "Fly.io:1년"
        )
        val targetRole = "AI Platform DevOps Engineer"

        val haiku = SkillAssessmentEvaluator(haikuClient, executor).evaluate(skills, targetRole)
        val sonnet = SkillAssessmentEvaluator(sonnetClient, executor).evaluate(skills, targetRole)

        println("\n" + "=".repeat(60))
        println("  기술 스택 평가: Haiku vs Sonnet")
        println("=".repeat(60))
        println("%-20s | %-10s | %-10s".format("항목", "Haiku", "Sonnet"))
        println("-".repeat(44))
        println("%-20s | %-10s | %-10s".format("총점", haiku.score, sonnet.score))
        println("%-20s | %-10s | %-10s".format("등급", haiku.grade, sonnet.grade))
        println("%-20s | %-10s | %-10s".format("점수 차이", "", Math.abs(haiku.score - sonnet.score)))
        println()
        println("[Haiku] strengths: ${haiku.strengths.take(2)}")
        println("[Sonnet] strengths: ${sonnet.strengths.take(2)}")
        println()
        println("[Haiku] improvements: ${haiku.improvements.take(2)}")
        println("[Sonnet] improvements: ${sonnet.improvements.take(2)}")
        println("=".repeat(60))
    }

    // ── 모의 면접 평가 비교 ───────────────────────────────────────────────

    @Test
    fun `모델 비교 - 모의 면접 평가 Haiku vs Sonnet`() {
        val question = "AI 서비스의 비용과 품질을 동시에 관리하려면 어떤 전략이 필요한가요?"
        val answer = """
            작업 중요도에 따라 모델을 라우팅했습니다.
            일반 퀘스트는 Haiku, 합격을 좌우하는 보스 퀘스트는 Sonnet을 사용했습니다.
            또한 시스템 프롬프트를 Anthropic 캐싱으로 처리해 반복 호출 비용을 줄였고,
            CacheMetricsAdvisor로 모든 호출의 토큰 사용량과 캐시 히트율을 Prometheus에 기록했습니다.
            호출 실패 시 3회 재시도하고 실패율이 임계치를 넘으면 알람을 거는 구조도 갖췄습니다.
        """.trimIndent()

        val haiku = MockInterviewEvaluator(haikuClient, executor).evaluate(
            category = "AI 운영", question = question, answer = answer,
            questionId = "compare-1", techStack = listOf("Spring AI", "Anthropic", "Prometheus"),
            yearsOfExperience = "3-5년"
        )
        val sonnet = MockInterviewEvaluator(sonnetClient, executor).evaluate(
            category = "AI 운영", question = question, answer = answer,
            questionId = "compare-1", techStack = listOf("Spring AI", "Anthropic", "Prometheus"),
            yearsOfExperience = "3-5년"
        )

        println("\n" + "=".repeat(60))
        println("  모의 면접 평가: Haiku vs Sonnet")
        println("=".repeat(60))
        println("%-20s | %-10s | %-10s".format("항목", "Haiku", "Sonnet"))
        println("-".repeat(44))
        println("%-20s | %-10s | %-10s".format("총점", haiku.score, sonnet.score))
        println("%-20s | %-10s | %-10s".format("기술 정확성(40)", haiku.technicalAccuracy, sonnet.technicalAccuracy))
        println("%-20s | %-10s | %-10s".format("깊이·응용(30)", haiku.depthAndApplication, sonnet.depthAndApplication))
        println("%-20s | %-10s | %-10s".format("실무경험(20)", haiku.practicalExperience, sonnet.practicalExperience))
        println("%-20s | %-10s | %-10s".format("커뮤니케이션(10)", haiku.communicationClarity, sonnet.communicationClarity))
        println("%-20s | %-10s | %-10s".format("점수 차이", "", Math.abs(haiku.score - sonnet.score)))
        println()
        println("[Haiku] keyPointsMissed: ${haiku.keyPointsMissed}")
        println("[Sonnet] keyPointsMissed: ${sonnet.keyPointsMissed}")
        println()
        println("[Haiku] improvements: ${haiku.improvements.take(120)}...")
        println("[Sonnet] improvements: ${sonnet.improvements.take(120)}...")
        println("=".repeat(60))
    }
}
