package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.ApiControllerAdvice
import com.devquest.core.domain.CompanyService
import com.devquest.core.domain.model.ActivityType
import com.devquest.core.domain.model.AppliedCompany
import com.devquest.core.domain.model.ApplicationStatus
import com.devquest.core.domain.model.CompanyActivity
import com.devquest.core.domain.model.evaluation.JdAnalysisResult
import com.devquest.core.domain.model.evaluation.ResumeCheckResult
import com.devquest.core.support.error.CoreException
import com.devquest.core.support.error.ErrorType
import java.time.LocalDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class CompanyControllerTest {

    @Mock
    private lateinit var companyService: CompanyService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("user-1", null, emptyList())
        mockMvc = MockMvcBuilders
            .standaloneSetup(CompanyController(companyService))
            .setControllerAdvice(ApiControllerAdvice())
            .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
            .build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `POST companies - 정상 요청이면 201과 SUCCESS 반환`() {
        whenever(companyService.createCompany(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            AppliedCompany(id = 1L, userId = "user-1", companyName = "카카오", position = "백엔드")
        )

        mockMvc.post("/api/v1/companies") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"companyName":"카카오","position":"백엔드"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.result") { value("SUCCESS") }
            jsonPath("$.data.companyName") { value("카카오") }
        }

        verify(companyService).createCompany("user-1", "카카오", "백엔드", null, null)
    }

    @Test
    fun `POST companies - companyName 없으면 400 반환`() {
        mockMvc.post("/api/v1/companies") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"position":"백엔드"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.result") { value("ERROR") }
        }
    }

    @Test
    fun `GET companies - 정상 요청이면 200과 목록 반환`() {
        whenever(companyService.getCompanies("user-1")).thenReturn(
            listOf(
                AppliedCompany(id = 1L, userId = "user-1", companyName = "카카오"),
                AppliedCompany(id = 2L, userId = "user-1", companyName = "네이버"),
            )
        )

        mockMvc.get("/api/v1/companies")
            .andExpect {
                status { isOk() }
                jsonPath("$.result") { value("SUCCESS") }
                jsonPath("$.data.length()") { value(2) }
                jsonPath("$.data[0].companyName") { value("카카오") }
            }
    }

    @Test
    fun `GET companies - 목록이 비어있으면 빈 배열 반환`() {
        whenever(companyService.getCompanies("user-1")).thenReturn(emptyList())

        mockMvc.get("/api/v1/companies")
            .andExpect {
                status { isOk() }
                jsonPath("$.result") { value("SUCCESS") }
                jsonPath("$.data.length()") { value(0) }
            }
    }

    @Test
    fun `PATCH companies id status - 정상 요청이면 200과 변경된 상태 반환`() {
        whenever(companyService.updateStatus(any<String>(), any<Long>(), any<ApplicationStatus>(), anyOrNull())).thenReturn(
            AppliedCompany(id = 1L, userId = "user-1", companyName = "카카오", status = ApplicationStatus.APPLIED)
        )

        mockMvc.patch("/api/v1/companies/1/status") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"APPLIED"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
            jsonPath("$.data.status") { value("APPLIED") }
        }
    }

    @Test
    fun `PATCH companies id status - 존재하지 않는 회사면 404 반환`() {
        whenever(companyService.updateStatus(any<String>(), any<Long>(), any<ApplicationStatus>(), anyOrNull()))
            .thenThrow(CoreException(ErrorType.COMPANY_NOT_FOUND))

        mockMvc.patch("/api/v1/companies/999/status") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"APPLIED"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.result") { value("ERROR") }
        }
    }

    @Test
    fun `DELETE companies id - 정상 요청이면 204 반환`() {
        mockMvc.delete("/api/v1/companies/1")
            .andExpect {
                status { isNoContent() }
            }

        verify(companyService).deleteCompany("user-1", 1L)
    }

    @Test
    fun `DELETE companies id - 존재하지 않는 회사면 404 반환`() {
        whenever(companyService.deleteCompany(any<String>(), any<Long>()))
            .thenThrow(CoreException(ErrorType.COMPANY_NOT_FOUND))

        mockMvc.delete("/api/v1/companies/999")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.result") { value("ERROR") }
            }
    }

    // ===== analyzeCompany 테스트 =====

    @Test
    fun `POST companies id analyze - 정상 요청이면 200과 분석 결과 반환`() {
        whenever(companyService.analyzeCompany(any(), any(), any(), any()))
            .thenReturn(JdAnalysisResult(companyName = "카카오", overallMatchScore = 85, passed = true))

        mockMvc.post("/api/v1/companies/1/analyze") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userSkills":["Java","Spring"],"userExperiences":["3년 백엔드"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
            jsonPath("$.data.companyName") { value("카카오") }
            jsonPath("$.data.overallMatchScore") { value(85) }
        }
    }

    @Test
    fun `POST companies id analyze - JD 없는 회사면 400 반환`() {
        whenever(companyService.analyzeCompany(any(), any(), any(), any()))
            .thenThrow(CoreException(ErrorType.INVALID_REQUEST))

        mockMvc.post("/api/v1/companies/1/analyze") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userSkills":["Java"],"userExperiences":["3년 백엔드"]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.result") { value("ERROR") }
        }
    }

    @Test
    fun `POST companies id analyze - 없는 회사면 404 반환`() {
        whenever(companyService.analyzeCompany(any(), any(), any(), any()))
            .thenThrow(CoreException(ErrorType.COMPANY_NOT_FOUND))

        mockMvc.post("/api/v1/companies/999/analyze") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userSkills":["Java"],"userExperiences":["3년 백엔드"]}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.result") { value("ERROR") }
        }
    }

    @Test
    fun `POST companies id analyze - userSkills 빈 배열이면 이력서 기반으로 분석한다`() {
        whenever(companyService.analyzeCompany(any(), any(), any(), any()))
            .thenReturn(JdAnalysisResult(companyName = "카카오", overallMatchScore = 88, passed = true))

        mockMvc.post("/api/v1/companies/1/analyze") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userSkills":[],"userExperiences":[]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
            jsonPath("$.data.overallMatchScore") { value(88) }
        }
    }

    @Test
    fun `POST companies id analyze - 리스트가 비어있고 이력서도 없으면 400 반환`() {
        whenever(companyService.analyzeCompany(any(), any(), any(), any()))
            .thenThrow(CoreException(ErrorType.RESUME_NOT_REGISTERED))

        mockMvc.post("/api/v1/companies/1/analyze") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userSkills":[],"userExperiences":[]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.result") { value("ERROR") }
        }
    }

    // ===== checkResume 테스트 =====

    @Test
    fun `POST companies id resume-check - 정상 요청이면 200과 점검 결과 반환`() {
        val checkedAt = LocalDateTime.of(2026, 7, 7, 12, 0)
        whenever(companyService.checkResume(any(), any()))
            .thenReturn(ResumeCheckResult(overallScore = 90, passed = true) to checkedAt)

        mockMvc.post("/api/v1/companies/1/resume-check")
            .andExpect {
                status { isOk() }
                jsonPath("$.result") { value("SUCCESS") }
                jsonPath("$.data.overallScore") { value(90) }
                jsonPath("$.data.passed") { value(true) }
            }
    }

    @Test
    fun `POST companies id resume-check - JD 미등록이면 400 반환`() {
        whenever(companyService.checkResume(any(), any()))
            .thenThrow(CoreException(ErrorType.COMPANY_JD_NOT_REGISTERED))

        mockMvc.post("/api/v1/companies/1/resume-check")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.result") { value("ERROR") }
            }
    }

    @Test
    fun `POST companies id resume-check - 이력서 미등록이면 400 반환`() {
        whenever(companyService.checkResume(any(), any()))
            .thenThrow(CoreException(ErrorType.RESUME_NOT_REGISTERED))

        mockMvc.post("/api/v1/companies/1/resume-check")
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.result") { value("ERROR") }
            }
    }

    @Test
    fun `POST companies id resume-check - 없는 회사면 404 반환`() {
        whenever(companyService.checkResume(any(), any()))
            .thenThrow(CoreException(ErrorType.COMPANY_NOT_FOUND))

        mockMvc.post("/api/v1/companies/999/resume-check")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.result") { value("ERROR") }
            }
    }

    // ===== getActivities 테스트 =====

    @Test
    fun `GET companies id activities - 정상 요청이면 200과 활동 목록 반환`() {
        whenever(companyService.getActivities(any(), any())).thenReturn(
            listOf(
                CompanyActivity(id = 2L, companyId = 1L, userId = "user-1", activityType = ActivityType.RESUME_CHECK),
                CompanyActivity(id = 1L, companyId = 1L, userId = "user-1", activityType = ActivityType.JD_ANALYSIS),
            )
        )

        mockMvc.get("/api/v1/companies/1/activities")
            .andExpect {
                status { isOk() }
                jsonPath("$.result") { value("SUCCESS") }
                jsonPath("$.data.length()") { value(2) }
            }
    }

    @Test
    fun `GET companies id activities - 없는 회사면 404 반환`() {
        whenever(companyService.getActivities(any(), any()))
            .thenThrow(CoreException(ErrorType.COMPANY_NOT_FOUND))

        mockMvc.get("/api/v1/companies/999/activities")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.result") { value("ERROR") }
            }
    }
}
