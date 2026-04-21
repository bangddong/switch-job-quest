package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.v1.request.*
import com.devquest.core.domain.AiCheckService
import com.devquest.core.domain.port.CompanyInfo
import com.devquest.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/ai-check")
class AiCheckController(
    private val aiCheckService: AiCheckService
) {

    @PostMapping("/skill-assessment")
    fun checkSkillAssessment(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: SkillAssessmentRequestDto,
    ): ApiResponse<*> {
        return ApiResponse.success(aiCheckService.checkSkillAssessment(userId, request.skills, request.targetRole))
    }

    @PostMapping("/career-essay")
    fun checkCareerEssay(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: CareerEssayRequestDto,
    ): ApiResponse<*> {
        return ApiResponse.success(
            aiCheckService.checkCareerEssay(userId, request.dissatisfactions, request.goals, request.fiveYearVision)
        )
    }

    @PostMapping("/company-fit")
    fun analyzeCompanyFit(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: CompanyFitRequestDto,
    ): ApiResponse<*> {
        val companies = request.companies.map {
            CompanyInfo(it.name, it.culture, it.techStack, it.size, it.description)
        }
        return ApiResponse.success(aiCheckService.analyzeCompanyFit(userId, request.preferences, companies))
    }

    @PostMapping("/tech-blog")
    fun checkTechBlog(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: TechBlogRequestDto,
    ): ApiResponse<*> {
        return ApiResponse.success(
            aiCheckService.checkTechBlog(userId, request.questId, request.techTopic, request.title, request.content)
        )
    }

    @PostMapping("/system-design")
    fun checkSystemDesign(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: SystemDesignRequestDto,
    ): ApiResponse<*> {
        return ApiResponse.success(
            aiCheckService.checkSystemDesign(
                userId, request.questId, request.problemStatement, request.architectureDescription, request.considerations
            )
        )
    }

    @PostMapping("/mock-interview")
    fun checkMockInterview(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: MockInterviewRequestDto,
    ): ApiResponse<*> {
        return ApiResponse.success(
            aiCheckService.checkMockInterview(
                userId, request.questId, request.category, request.question, request.answer, request.questionId
            )
        )
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
    fun analyzeJd(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: JdAnalysisRequestDto,
    ): ApiResponse<*> {
        return ApiResponse.success(
            aiCheckService.analyzeJd(
                userId, request.companyName, request.jobDescription, request.userSkills, request.userExperiences
            )
        )
    }

    @PostMapping("/resume")
    fun checkResume(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: ResumeCheckRequestDto,
    ): ApiResponse<*> {
        return ApiResponse.success(
            aiCheckService.checkResume(userId, request.targetCompany, request.targetJd, request.resumeContent)
        )
    }

    @PostMapping("/personality-interview")
    fun checkPersonalityInterview(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: PersonalityInterviewRequestDto,
    ): ApiResponse<*> {
        return ApiResponse.success(
            aiCheckService.checkPersonalityInterview(userId, request.question, request.answer)
        )
    }

    @PostMapping("/developer-class")
    fun evaluateDeveloperClass(@AuthenticationPrincipal userId: String): ApiResponse<*> {
        return ApiResponse.success(aiCheckService.evaluateDeveloperClass(userId))
    }

    @PostMapping("/boss-package")
    fun checkBossPackage(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: BossPackageRequestDto,
    ): ApiResponse<*> {
        return ApiResponse.success(
            aiCheckService.checkBossPackage(
                userId, request.resumeContent, request.githubUrl, request.blogUrl, request.targetPosition
            )
        )
    }

    @PostMapping("/journey-report")
    fun generateJourneyReport(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: JourneyReportRequestDto,
    ): ApiResponse<*> {
        return ApiResponse.success(
            aiCheckService.generateJourneyReport(userId, request.companyName, request.targetPosition)
        )
    }

    @PostMapping("/act-clear-report")
    fun generateActClearReport(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: ActClearReportRequestDto,
    ): ApiResponse<*> {
        return ApiResponse.success(
            aiCheckService.generateActClearReport(userId, request.actId, request.actTitle)
        )
    }
}
