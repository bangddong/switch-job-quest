package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.v1.request.*
import com.devquest.core.domain.AiCheckService
import com.devquest.core.domain.port.CompanyInfo
import com.devquest.core.support.error.CoreException
import com.devquest.core.support.error.ErrorType
import com.devquest.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/ai-check")
class AiCheckController(
    private val aiCheckService: AiCheckService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/skill-assessment")
    fun checkSkillAssessment(@Valid @RequestBody request: SkillAssessmentRequestDto): ApiResponse<*> {
        return try {
            ApiResponse.success(aiCheckService.checkSkillAssessment(request.userId, request.skills, request.targetRole))
        } catch (e: Exception) {
            log.error("Skill assessment failed", e)
            throw CoreException(ErrorType.AI_EVALUATION_FAILED)
        }
    }

    @PostMapping("/career-essay")
    fun checkCareerEssay(@Valid @RequestBody request: CareerEssayRequestDto): ApiResponse<*> {
        return try {
            val result = aiCheckService.checkCareerEssay(
                request.userId, request.dissatisfactions, request.goals, request.fiveYearVision
            )
            ApiResponse.success(result)
        } catch (e: Exception) {
            log.error("Career essay check failed", e)
            throw CoreException(ErrorType.AI_EVALUATION_FAILED)
        }
    }

    @PostMapping("/company-fit")
    fun analyzeCompanyFit(@Valid @RequestBody request: CompanyFitRequestDto): ApiResponse<*> {
        return try {
            val companies = request.companies.map {
                CompanyInfo(it.name, it.culture, it.techStack, it.size, it.description)
            }
            val result = aiCheckService.analyzeCompanyFit(request.userId, request.preferences, companies)
            ApiResponse.success(result)
        } catch (e: Exception) {
            log.error("Company fit analysis failed", e)
            throw CoreException(ErrorType.AI_EVALUATION_FAILED)
        }
    }

    @PostMapping("/tech-blog")
    fun checkTechBlog(@Valid @RequestBody request: TechBlogRequestDto): ApiResponse<*> {
        return try {
            val result = aiCheckService.checkTechBlog(
                request.userId, request.questId, request.techTopic, request.title, request.content
            )
            ApiResponse.success(result)
        } catch (e: Exception) {
            log.error("Tech blog check failed", e)
            throw CoreException(ErrorType.AI_EVALUATION_FAILED)
        }
    }

    @PostMapping("/system-design")
    fun checkSystemDesign(@Valid @RequestBody request: SystemDesignRequestDto): ApiResponse<*> {
        return try {
            val result = aiCheckService.checkSystemDesign(
                request.userId, request.questId, request.problemStatement, request.architectureDescription, request.considerations
            )
            ApiResponse.success(result)
        } catch (e: Exception) {
            log.error("System design check failed", e)
            throw CoreException(ErrorType.AI_EVALUATION_FAILED)
        }
    }

    @PostMapping("/mock-interview")
    fun checkMockInterview(@Valid @RequestBody request: MockInterviewRequestDto): ApiResponse<*> {
        return try {
            val result = aiCheckService.checkMockInterview(
                request.userId, request.questId, request.category, request.question, request.answer, request.questionId
            )
            ApiResponse.success(result)
        } catch (e: Exception) {
            log.error("Mock interview check failed", e)
            throw CoreException(ErrorType.AI_EVALUATION_FAILED)
        }
    }

    @GetMapping("/mock-interview/questions")
    fun generateInterviewQuestions(
        @RequestParam(defaultValue = "DB,JVM,네트워크,운영체제,설계") categories: String,
        @RequestParam(defaultValue = "10") count: Int
    ): ApiResponse<*> {
        val categoryList = categories.split(",").map { it.trim() }
        return ApiResponse.success(aiCheckService.generateInterviewQuestions(categoryList, count))
    }

    @PostMapping("/jd-analysis")
    fun analyzeJd(@Valid @RequestBody request: JdAnalysisRequestDto): ApiResponse<*> {
        return try {
            val result = aiCheckService.analyzeJd(
                request.userId, request.companyName, request.jobDescription, request.userSkills, request.userExperiences
            )
            ApiResponse.success(result)
        } catch (e: Exception) {
            log.error("JD analysis failed", e)
            throw CoreException(ErrorType.AI_EVALUATION_FAILED)
        }
    }

    @PostMapping("/resume")
    fun checkResume(@Valid @RequestBody request: ResumeCheckRequestDto): ApiResponse<*> {
        return try {
            val result = aiCheckService.checkResume(
                request.userId, request.targetCompany, request.targetJd, request.resumeContent
            )
            ApiResponse.success(result)
        } catch (e: Exception) {
            log.error("Resume check failed", e)
            throw CoreException(ErrorType.AI_EVALUATION_FAILED)
        }
    }

    @PostMapping("/personality-interview")
    fun checkPersonalityInterview(@Valid @RequestBody request: PersonalityInterviewRequestDto): ApiResponse<*> {
        return try {
            val result = aiCheckService.checkPersonalityInterview(
                request.userId, request.question, request.answer
            )
            ApiResponse.success(result)
        } catch (e: Exception) {
            log.error("Personality interview check failed", e)
            throw CoreException(ErrorType.AI_EVALUATION_FAILED)
        }
    }

    @PostMapping("/boss-package")
    fun checkBossPackage(@Valid @RequestBody request: BossPackageRequestDto): ApiResponse<*> {
        return try {
            val result = aiCheckService.checkBossPackage(
                request.userId, request.resumeContent, request.githubUrl, request.blogUrl, request.targetPosition
            )
            ApiResponse.success(result)
        } catch (e: Exception) {
            log.error("Boss package check failed", e)
            throw CoreException(ErrorType.AI_EVALUATION_FAILED)
        }
    }

    @PostMapping("/journey-report")
    fun generateJourneyReport(@Valid @RequestBody request: JourneyReportRequestDto): ApiResponse<*> {
        return try {
            val result = aiCheckService.generateJourneyReport(request.userId, request.companyName, request.targetPosition)
            ApiResponse.success(result)
        } catch (e: Exception) {
            log.error("Journey report generation failed", e)
            throw CoreException(ErrorType.AI_EVALUATION_FAILED)
        }
    }

    @PostMapping("/act-clear-report")
    fun generateActClearReport(@Valid @RequestBody request: ActClearReportRequestDto): ApiResponse<*> {
        return try {
            val result = aiCheckService.generateActClearReport(request.userId, request.actId, request.actTitle)
            ApiResponse.success(result)
        } catch (e: Exception) {
            log.error("Act clear report generation failed", e)
            throw CoreException(ErrorType.AI_EVALUATION_FAILED)
        }
    }
}
