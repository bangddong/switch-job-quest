package com.devquest.parity

import com.devquest.core.api.adapter.ai.http.BlogHttpEvaluator
import com.devquest.core.api.adapter.ai.http.CompanyFitHttpEvaluator
import com.devquest.core.api.adapter.ai.http.JdAnalysisHttpEvaluator
import com.devquest.core.api.adapter.ai.http.ResumeHttpEvaluator
import com.devquest.core.api.adapter.ai.http.TechInterviewHttpAdapter
import com.devquest.core.api.config.buildAiApiRestClient
import com.devquest.core.domain.model.evaluation.AiEvaluationResult
import com.devquest.core.domain.model.evaluation.CompanyFitResult
import com.devquest.core.domain.model.evaluation.JdAnalysisResult
import com.devquest.core.domain.model.evaluation.ResumeCheckResult
import com.devquest.core.domain.model.evaluation.ResumeImprovement
import com.devquest.core.domain.model.evaluation.ResumeRewrite
import com.devquest.core.domain.model.evaluation.SkillRequirement
import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.BlogEvaluatorPort
import com.devquest.core.domain.port.CompanyFitEvaluatorPort
import com.devquest.core.domain.port.CompanyInfo
import com.devquest.core.domain.port.JdAnalysisEvaluatorPort
import com.devquest.core.domain.port.ResumeEvaluatorPort
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.core.domain.support.AiEvaluationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.bean.override.mockito.MockitoBean
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/**
 * Phase 1 Task 1.5 — in-process(포트 목 반환값)와 http(core HTTP 어댑터 → 실제 ai-api → 실제 응답)
 * 결과가 동등한지를 실측한다.
 *
 * **테스트 배치 근거**: ai-api·core-api는 서로 독립 Gradle 모듈이라 한 프로세스에서 "진짜 ai-api
 * 컨텍스트 + core의 HTTP 어댑터"를 동시에 다루려면 모듈 경계를 넘는 테스트 전용 의존이 필요하다.
 * `core-api/build.gradle.kts`에 `testImplementation(project(":core:ai-api"))`를 추가해 이 테스트
 * 소스셋에서만 ai-api 클래스를 끌어왔다(프로덕션 `implementation` 아님 — bootJar·Fly 배포 산출물
 * 무관, 롤백 불변식과도 무관). `AiApiApplication`을 그대로 쓰지 않고 스캔 범위를 좁힌
 * [AiApiParityTestApplication]을 쓰는 이유·설정 파일명이 `application.yml`이 아니라
 * `ai-api-parity-test.yml`인 이유는 각각의 KDoc/주석에 근거를 남겼다.
 *
 * **AI 포트는 전부 `@MockitoBean`으로 대체** — 실제 LLM 호출은 0건. ai-api의
 * `TechInterviewWireFormatContractTest`가 이미 같은 패턴(RANDOM_PORT + MockitoBean)을 증명해뒀다.
 *
 * **동등성 판정 기준**: 이 테스트는 LLM 출력의 비결정성을 검증하지 않는다(포트가 목이라 값이
 * 결정적이다) — 대신 **Jackson 직렬화/역직렬화 라운드트립이 필드를 손실 없이 보존하는지**를
 * 검증하는 것이 목적이므로, 스키마 동등이 아니라 **정확 일치(`isEqualTo`)**를 쓴다. 이게 계획 문서가
 * 요구하는 "결정적인 부분(요청 매핑·기본값 복원·에러 코드·필드명)은 정확 일치"보다 더 엄격한
 * 기준이며, Map·List<데이터클래스 중첩>·nullable·default 파라미터를 포함하는 포트를 우선
 * 선정했다(BaseAiHttpAdapter Jackson 2→3 전환의 QA 우려가 실제로 드러날 지점).
 *
 * 대표 포트 4개(Blog·Resume·TechInterview·CompanyFit, 계획 명시) + JdAnalysis(기본값 소실 대응
 * 사례가 있어 추가) = 5개.
 */
@SpringBootTest(
    classes = [AiApiParityTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.config.name=ai-api-parity-test"],
)
class AiApiHttpParityTest {

    @LocalServerPort
    private var port: Int = 0

    @MockitoBean
    private lateinit var blogEvaluatorPort: BlogEvaluatorPort

    @MockitoBean
    private lateinit var resumeEvaluatorPort: ResumeEvaluatorPort

    @MockitoBean
    private lateinit var techInterviewPort: TechInterviewPort

    @MockitoBean
    private lateinit var companyFitEvaluatorPort: CompanyFitEvaluatorPort

    @MockitoBean
    private lateinit var jdAnalysisEvaluatorPort: JdAnalysisEvaluatorPort

    private val objectMapper = jacksonObjectMapper()
    private val httpClient = HttpClient.newHttpClient()

    private fun restClient() = buildAiApiRestClient(
        baseUrl = "http://localhost:$port",
        connectTimeoutMs = 3000,
        readTimeoutMs = 15000,
    )

    // ===== Blog — 평평한(nested 없는) 결과 정확 일치 =====

    @Test
    fun `blog - in-process 목 결과와 http 라운드트립 결과가 완전히 동일하다`() {
        val expected = AiEvaluationResult(
            score = 88,
            passed = true,
            grade = "A",
            summary = "요약",
            strengths = listOf("강점1", "강점2"),
            improvements = listOf("개선1"),
            detailedFeedback = "상세 피드백 한글 텍스트",
            xpMultiplier = 1.5,
            retryAllowed = false,
        )
        whenever(blogEvaluatorPort.evaluate(any(), any(), any())).thenReturn(expected)

        val httpResult = BlogHttpEvaluator(restClient(), objectMapper).evaluate("Kotlin", "코루틴 완전정복", "본문")

        assertThat(httpResult).isEqualTo(expected)
        // 요청 매핑 정확 일치 — 어댑터가 보낸 값이 그대로 포트에 도달했는지.
        verify(blogEvaluatorPort).evaluate("Kotlin", "코루틴 완전정복", "본문")
    }

    // ===== Resume — List<중첩 데이터클래스> 2종 결과 정확 일치 =====

    @Test
    fun `resume - 중첩 리스트(improvements, rewrittenExamples) 필드가 손실 없이 왕복한다`() {
        val expected = ResumeCheckResult(
            overallScore = 82,
            passed = true,
            starMethodScore = 20,
            quantificationScore = 18,
            keywordMatchScore = 19,
            improvements = listOf(
                ResumeImprovement(section = "경력", original = "원문1", issue = "이슈1", suggestion = "제안1"),
                ResumeImprovement(section = "기술스택", original = "원문2", issue = "이슈2", suggestion = "제안2"),
            ),
            rewrittenExamples = listOf(
                ResumeRewrite(original = "원본", improved = "개선본", explanation = "설명"),
            ),
        )
        whenever(resumeEvaluatorPort.evaluate(any(), any(), any())).thenReturn(expected)

        val httpResult = ResumeHttpEvaluator(restClient(), objectMapper)
            .evaluate("카카오", "JD 내용", "이력서 본문")

        assertThat(httpResult).isEqualTo(expected)
        verify(resumeEvaluatorPort).evaluate("카카오", "JD 내용", "이력서 본문")
    }

    // ===== CompanyFit — Map<String,String> 요청 + List<중첩> 요청/응답 =====

    @Test
    fun `companyFit - Map 요청 파라미터와 List 중첩 요청 응답이 손실 없이 왕복한다`() {
        val preferences = linkedMapOf("culture" to "수평적", "location" to "서울", "size" to "중견")
        val companies = listOf(
            CompanyInfo(
                name = "A사",
                culture = "자율적",
                techStack = listOf("Kotlin", "Spring"),
                size = "대기업",
                description = "설명1",
            ),
            CompanyInfo(
                name = "B사",
                culture = "수평적",
                techStack = emptyList(),
                size = "스타트업",
                description = "설명2",
            ),
        )
        val expected = listOf(
            CompanyFitResult(
                companyName = "A사",
                fitScore = 90,
                fitGrade = "A",
                cultureFit = 20,
                techFit = 25,
                growthFit = 20,
                lifestyleFit = 25,
                pros = listOf("장점1", "장점2"),
                cons = emptyList(),
                recommendation = "추천",
            ),
            CompanyFitResult(companyName = "B사", fitScore = 40, fitGrade = "D"),
        )
        whenever(companyFitEvaluatorPort.analyze(any(), any())).thenReturn(expected)

        val httpResult = CompanyFitHttpEvaluator(restClient(), objectMapper).analyze(preferences, companies)

        assertThat(httpResult).isEqualTo(expected)
        verify(companyFitEvaluatorPort).analyze(eq(preferences), eq(companies))
    }

    // ===== TechInterview — questions/evaluate(JSON) + daily-question/explain-followup(text/plain, nullable/default) =====

    @Test
    fun `techInterview generateQuestions - JSON 결과가 정확히 일치한다`() {
        val expected = TechInterviewResult(
            questions = listOf("질문1", "질문2"),
            overallScore = 0,
            feedback = "",
            passed = false,
            modelAnswer = "",
        )
        whenever(techInterviewPort.generateQuestions(any())).thenReturn(expected)

        val httpResult = TechInterviewHttpAdapter(restClient(), objectMapper).generateQuestions("Kotlin")

        assertThat(httpResult).isEqualTo(expected)
        verify(techInterviewPort).generateQuestions("Kotlin")
    }

    @Test
    fun `techInterview evaluate - JSON 결과가 정확히 일치한다`() {
        val expected = TechInterviewResult(
            questions = listOf("q1", "q2"),
            overallScore = 77,
            feedback = "피드백",
            passed = true,
            modelAnswer = "모범 답안",
        )
        whenever(techInterviewPort.evaluate(any(), any(), any())).thenReturn(expected)

        val httpResult = TechInterviewHttpAdapter(restClient(), objectMapper)
            .evaluate("Kotlin", listOf("q1", "q2"), listOf("a1", "a2"))

        assertThat(httpResult).isEqualTo(expected)
        verify(techInterviewPort).evaluate("Kotlin", listOf("q1", "q2"), listOf("a1", "a2"))
    }

    @Test
    fun `techInterview generateDailyQuestion - text-plain 응답이 원문 그대로 왕복한다`() {
        whenever(techInterviewPort.generateDailyQuestion(any(), any())).thenReturn("오늘의 질문입니다")

        val httpResult = TechInterviewHttpAdapter(restClient(), objectMapper)
            .generateDailyQuestion("Kotlin", listOf("이전질문1"))

        assertThat(httpResult).isEqualTo("오늘의 질문입니다")
        verify(techInterviewPort).generateDailyQuestion("Kotlin", listOf("이전질문1"))
    }

    @Test
    fun `techInterview explainFollowup - 명시적 null modelAnswer가 끝까지 null로 왕복한다`() {
        whenever(techInterviewPort.explainFollowup(any(), any(), any(), any(), isNull())).thenReturn("설명입니다")

        val httpResult = TechInterviewHttpAdapter(restClient(), objectMapper)
            .explainFollowup("질문", "답변", "피드백", "추가질문", null)

        assertThat(httpResult).isEqualTo("설명입니다")
        verify(techInterviewPort).explainFollowup("질문", "답변", "피드백", "추가질문", null)
    }

    /**
     * 어댑터([TechInterviewHttpAdapter])는 절대 이 케이스를 만들지 못한다 — `recentQuestions`가
     * `TechInterviewPort` 인터페이스의 기본값(`= emptyList()`)을 갖고 있어, 어댑터를 포트 타입으로
     * 호출하는 순간 Kotlin 컴파일러가 호출부에서 기본값을 이미 채워 넣기 때문이다(오버라이드는 기본값을
     * 재선언할 수 없음 — TechInterviewHttpAdapter KDoc 참고). 그래서 필드 생략은 raw HTTP로만 재현
     * 가능하다 — ai-api 컨트롤러의 "필드 생략 시 서버측 기본값 복원" 로직이 **실제 프로덕션 Jackson
     * 설정**(J3 + Kotlin 모듈, 이 테스트가 로드하는 진짜 내장 서버)으로도 동일하게 동작하는지 실측한다.
     */
    @Test
    fun `techInterview daily-question - recentQuestions 필드를 생략하면 서버가 빈 리스트로 복원한다 (raw wire 테스트)`() {
        whenever(techInterviewPort.generateDailyQuestion(any(), any())).thenReturn("오늘의 질문")

        val response = rawPost("/internal/ai/tech-interview/daily-question", """{"techStack":"Kotlin"}""")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(String(response.body(), StandardCharsets.UTF_8)).isEqualTo("오늘의 질문")
        verify(techInterviewPort).generateDailyQuestion("Kotlin", emptyList())
    }

    // ===== JdAnalysis — List<중첩> 응답 + 기본값 소실 대응(resumeContent) =====

    @Test
    fun `jdAnalysis - resumeContent 명시 전달 시 결과(중첩 List 포함)가 정확히 일치한다`() {
        val expected = JdAnalysisResult(
            companyName = "카카오",
            requiredSkills = listOf(
                SkillRequirement(skill = "Kotlin", required = true, userLevel = "중급", importance = "높음"),
                SkillRequirement(skill = "AWS", required = false, userLevel = "초급", importance = "보통"),
            ),
            hiddenRequirements = listOf("커뮤니케이션"),
            overallMatchScore = 78,
            passed = true,
            keyDifferentiators = listOf("차별점1"),
            applicationStrategy = "전략",
        )
        whenever(jdAnalysisEvaluatorPort.analyze(any(), any(), any(), any(), any())).thenReturn(expected)

        val httpResult = JdAnalysisHttpEvaluator(restClient(), objectMapper)
            .analyze("카카오", "JD 내용", listOf("Kotlin"), listOf("3년 경력"), "이력서 본문")

        assertThat(httpResult).isEqualTo(expected)
        verify(jdAnalysisEvaluatorPort).analyze("카카오", "JD 내용", listOf("Kotlin"), listOf("3년 경력"), "이력서 본문")
    }

    /**
     * TechInterview의 daily-question 케이스와 동일한 이유로 raw HTTP로만 재현 가능 —
     * `JdAnalysisEvaluatorPort.analyze`의 `resumeContent = ""` 기본값은 어댑터 호출 시점에 이미
     * 채워지므로, 실제로 필드가 빠진 JSON은 이 포트를 직접 호출하는 다른(가상의) 클라이언트를
     * 흉내내야 한다.
     */
    @Test
    fun `jdAnalysis - resumeContent 필드를 생략하면 서버가 빈 문자열로 복원한다 (raw wire 테스트)`() {
        whenever(jdAnalysisEvaluatorPort.analyze(any(), any(), any(), any(), any()))
            .thenReturn(JdAnalysisResult(companyName = "카카오"))

        val body = """{"companyName":"카카오","jobDescription":"JD","userSkills":["Kotlin"],"userExperiences":["3년"]}"""
        val response = rawPost("/internal/ai/jd-analysis/analyze", body)

        assertThat(response.statusCode()).isEqualTo(200)
        verify(jdAnalysisEvaluatorPort).analyze("카카오", "JD", listOf("Kotlin"), listOf("3년"), "")
    }

    // ===== 에러 경로 — 실제 ai-api 500/필드명·상태코드 정확 일치 =====

    @Test
    fun `평가자가 예외를 던지면 실제 ai-api의 500 message가 AiEvaluationException에 그대로 실린다`() {
        whenever(blogEvaluatorPort.evaluate(any(), any(), any()))
            .thenThrow(RuntimeException("평가 중 실패했습니다"))

        assertThatThrownBy { BlogHttpEvaluator(restClient(), objectMapper).evaluate("Kotlin", "제목", "본문") }
            .isInstanceOf(AiEvaluationException::class.java)
            .hasMessageContaining("평가 중 실패했습니다")
    }

    @Test
    fun `필수 필드가 빠지면 실제 ai-api가 400과 정확한 상태코드 바디를 반환한다 (raw wire 테스트)`() {
        // techTopic 필드를 통째로 빼서 BlogEvaluateRequest 역직렬화 시점에 HttpMessageNotReadableException 유발.
        val response = rawPost("/internal/ai/blog/evaluate", """{"title":"제목","content":"본문"}""")
        val body = String(response.body(), StandardCharsets.UTF_8)

        assertThat(response.statusCode()).isEqualTo(400)
        assertThat(body).contains(""""status":400""")
        assertThat(body).contains(""""error":"Bad Request"""")
        assertThat(body).contains(""""path":"/internal/ai/blog/evaluate"""")
    }


    private fun rawPost(path: String, body: String): HttpResponse<ByteArray> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
    }
}
