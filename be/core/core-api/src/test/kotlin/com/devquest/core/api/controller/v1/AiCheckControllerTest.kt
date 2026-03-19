package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.ApiControllerAdvice
import com.devquest.core.domain.AiCheckService
import com.devquest.core.domain.model.evaluation.*
import com.devquest.core.domain.support.AiEvaluationException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class AiCheckControllerTest {

    @Mock
    private lateinit var aiCheckService: AiCheckService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(AiCheckController(aiCheckService))
            .setControllerAdvice(ApiControllerAdvice())
            .build()
    }

    // ===== /career-essay =====

    @Test
    fun `career-essay - 정상 요청이면 200과 SUCCESS 반환`() {
        whenever(aiCheckService.checkCareerEssay(any(), any(), any(), any()))
            .thenReturn(EssayCheckResult(score = 80, passed = true))

        mockMvc.post("/api/v1/ai-check/career-essay") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "userId": "user-1",
                  "dissatisfactions": ["불만1", "불만2", "불만3"],
                  "goals": ["목표1", "목표2", "목표3"],
                  "fiveYearVision": "5년 후 CTO"
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
            jsonPath("$.data.score") { value(80) }
            jsonPath("$.data.passed") { value(true) }
        }
    }

    @Test
    fun `career-essay - userId 없으면 400 INVALID_REQUEST 반환`() {
        mockMvc.post("/api/v1/ai-check/career-essay") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "userId": "",
                  "dissatisfactions": ["불만1", "불만2", "불만3"],
                  "goals": ["목표1", "목표2", "목표3"],
                  "fiveYearVision": "5년 후 CTO"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.result") { value("ERROR") }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `career-essay - dissatisfactions 항목 수 부족 시 400 반환`() {
        mockMvc.post("/api/v1/ai-check/career-essay") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "userId": "user-1",
                  "dissatisfactions": ["불만1"],
                  "goals": ["목표1", "목표2", "목표3"],
                  "fiveYearVision": "5년 후 CTO"
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `career-essay - JSON 파싱 불가 시 400 INVALID_REQUEST 반환`() {
        mockMvc.post("/api/v1/ai-check/career-essay") {
            contentType = MediaType.APPLICATION_JSON
            content = "{ invalid-json !!!"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `career-essay - AiEvaluationException 발생 시 500 AI_EVALUATION_FAILED 반환`() {
        whenever(aiCheckService.checkCareerEssay(any(), any(), any(), any()))
            .thenThrow(AiEvaluationException("AI 응답 파싱 실패"))

        mockMvc.post("/api/v1/ai-check/career-essay") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "userId": "user-1",
                  "dissatisfactions": ["불만1", "불만2", "불만3"],
                  "goals": ["목표1", "목표2", "목표3"],
                  "fiveYearVision": "5년 후 CTO"
                }
            """.trimIndent()
        }.andExpect {
            status { isInternalServerError() }
            jsonPath("$.error.code") { value("AI_EVALUATION_FAILED") }
        }
    }

    @Test
    fun `career-essay - userId 필드 자체가 없으면 400 반환`() {
        mockMvc.post("/api/v1/ai-check/career-essay") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"dissatisfactions":["불만1","불만2","불만3"],"goals":["목표1","목표2","목표3"],"fiveYearVision":"비전"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }
    }

    // ===== /company-fit =====

    @Test
    fun `company-fit - 정상 요청이면 200과 SUCCESS 반환`() {
        whenever(aiCheckService.analyzeCompanyFit(any(), any(), any()))
            .thenReturn(listOf(CompanyFitResult(companyName = "카카오", fitScore = 85)))

        mockMvc.post("/api/v1/ai-check/company-fit") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "userId": "user-1",
                  "preferences": {"culture": "수평적"},
                  "companies": [{"name":"카카오","culture":"수평","techStack":["Kotlin"],"size":"대기업","description":"카카오"}]
                }
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
            jsonPath("$.data[0].companyName") { value("카카오") }
        }
    }

    @Test
    fun `company-fit - userId 빈값이면 400 반환`() {
        mockMvc.post("/api/v1/ai-check/company-fit") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"","preferences":{"culture":"수평"},"companies":[{"name":"A","culture":"","techStack":[],"size":"","description":""}]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `company-fit - companies 빈 목록이면 400 반환`() {
        mockMvc.post("/api/v1/ai-check/company-fit") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"user-1","preferences":{"culture":"수평"},"companies":[]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }
    }

    // ===== /tech-blog =====

    @Test
    fun `tech-blog - 정상 요청이면 200과 SUCCESS 반환`() {
        whenever(aiCheckService.checkTechBlog(any(), any(), any(), any(), any()))
            .thenReturn(AiEvaluationResult(score = 75, passed = true))

        mockMvc.post("/api/v1/ai-check/tech-blog") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"user-1","questId":"2-1","techTopic":"Kotlin","title":"코루틴","content":"내용"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
            jsonPath("$.data.score") { value(75) }
        }
    }

    @Test
    fun `tech-blog - questId 빈값이면 400 반환`() {
        mockMvc.post("/api/v1/ai-check/tech-blog") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"user-1","questId":"","techTopic":"Kotlin","title":"코루틴","content":"내용"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }
    }

    // ===== /system-design =====

    @Test
    fun `system-design - 정상 요청이면 200과 SUCCESS 반환`() {
        whenever(aiCheckService.checkSystemDesign(any(), any(), any(), any(), any()))
            .thenReturn(AiEvaluationResult(score = 80, passed = true))

        mockMvc.post("/api/v1/ai-check/system-design") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"user-1","questId":"2-2","problemStatement":"문제","architectureDescription":"설계","considerations":["확장성"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
        }
    }

    @Test
    fun `system-design - considerations 빈 목록이면 400 반환`() {
        mockMvc.post("/api/v1/ai-check/system-design") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"user-1","questId":"2-2","problemStatement":"문제","architectureDescription":"설계","considerations":[]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }
    }

    // ===== /mock-interview =====

    @Test
    fun `mock-interview - 정상 요청이면 200과 SUCCESS 반환`() {
        whenever(aiCheckService.checkMockInterview(any(), any(), any(), any(), any(), any()))
            .thenReturn(InterviewEvaluationResult(score = 90, passed = true))

        mockMvc.post("/api/v1/ai-check/mock-interview") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"user-1","questId":"2-3","questionId":"q-1","question":"JVM이란?","answer":"JVM은...","category":"JVM"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
            jsonPath("$.data.score") { value(90) }
        }
    }

    @Test
    fun `mock-interview - answer 빈값이면 400 반환`() {
        mockMvc.post("/api/v1/ai-check/mock-interview") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"user-1","questId":"2-3","questionId":"q-1","question":"JVM이란?","answer":"","category":"JVM"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }
    }

    // ===== GET /mock-interview/questions =====

    @Test
    fun `mock-interview questions - 정상 요청이면 200과 SUCCESS 반환`() {
        whenever(aiCheckService.generateInterviewQuestions(any(), any()))
            .thenReturn(listOf(mapOf("questionId" to "q-1", "question" to "JVM이란?", "category" to "JVM")))

        mockMvc.get("/api/v1/ai-check/mock-interview/questions")
            .andExpect {
                status { isOk() }
                jsonPath("$.result") { value("SUCCESS") }
                jsonPath("$.data[0].category") { value("JVM") }
            }
    }

    // ===== /jd-analysis =====

    @Test
    fun `jd-analysis - 정상 요청이면 200과 SUCCESS 반환`() {
        whenever(aiCheckService.analyzeJd(any(), any(), any(), any(), any()))
            .thenReturn(JdAnalysisResult(companyName = "카카오", overallMatchScore = 70))

        mockMvc.post("/api/v1/ai-check/jd-analysis") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"user-1","companyName":"카카오","jobDescription":"JD 내용","userSkills":["Kotlin"],"userExperiences":["3년 경력"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
            jsonPath("$.data.companyName") { value("카카오") }
        }
    }

    @Test
    fun `jd-analysis - userSkills 빈 목록이면 400 반환`() {
        mockMvc.post("/api/v1/ai-check/jd-analysis") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"user-1","companyName":"카카오","jobDescription":"JD 내용","userSkills":[],"userExperiences":["3년"]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }
    }

    // ===== /resume =====

    @Test
    fun `resume - 정상 요청이면 200과 SUCCESS 반환`() {
        whenever(aiCheckService.checkResume(any(), any(), any(), any()))
            .thenReturn(ResumeCheckResult(overallScore = 85))

        mockMvc.post("/api/v1/ai-check/resume") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"user-1","targetCompany":"카카오","targetJd":"JD","resumeContent":"이력서"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
            jsonPath("$.data.overallScore") { value(85) }
        }
    }

    @Test
    fun `resume - resumeContent 빈값이면 400 반환`() {
        mockMvc.post("/api/v1/ai-check/resume") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"user-1","targetCompany":"카카오","targetJd":"JD","resumeContent":""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }
    }

    // ===== /personality-interview =====

    @Test
    fun `personality-interview - 정상 요청이면 200과 SUCCESS 반환`() {
        whenever(aiCheckService.checkPersonalityInterview(any(), any(), any()))
            .thenReturn(AiEvaluationResult(score = 88, passed = true))

        mockMvc.post("/api/v1/ai-check/personality-interview") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"user-1","question":"장단점은?","answer":"저는..."}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
            jsonPath("$.data.score") { value(88) }
        }
    }

    @Test
    fun `personality-interview - question 빈값이면 400 반환`() {
        mockMvc.post("/api/v1/ai-check/personality-interview") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"user-1","question":"","answer":"저는..."}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }
    }
}
