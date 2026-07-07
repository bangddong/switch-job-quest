package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.v1.request.AnalyzeCompanyRequestDto
import com.devquest.core.api.controller.v1.request.CreateCompanyRequestDto
import com.devquest.core.api.controller.v1.request.UpdateCompanyStatusRequestDto
import com.devquest.core.api.controller.v1.response.ResumeCheckResponseDto
import com.devquest.core.domain.CompanyService
import com.devquest.core.support.response.ApiResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/companies")
class CompanyController(
    private val companyService: CompanyService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCompany(
        @AuthenticationPrincipal userId: String,
        @Valid @RequestBody request: CreateCompanyRequestDto,
    ): ApiResponse<*> {
        val result = companyService.createCompany(userId, request.companyName, request.position, request.jdUrl, request.jobDescription)
        return ApiResponse.success(result)
    }

    @GetMapping
    fun getCompanies(
        @AuthenticationPrincipal userId: String,
    ): ApiResponse<*> {
        return ApiResponse.success(companyService.getCompanies(userId))
    }

    @PatchMapping("/{id}/status")
    fun updateStatus(
        @AuthenticationPrincipal userId: String,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateCompanyStatusRequestDto,
    ): ApiResponse<*> {
        val result = companyService.updateStatus(userId, id, request.status, request.appliedAt)
        return ApiResponse.success(result)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCompany(
        @AuthenticationPrincipal userId: String,
        @PathVariable id: Long,
    ) {
        companyService.deleteCompany(userId, id)
    }

    @PostMapping("/{id}/analyze")
    fun analyzeCompany(
        @AuthenticationPrincipal userId: String,
        @PathVariable id: Long,
        @Valid @RequestBody request: AnalyzeCompanyRequestDto,
    ): ApiResponse<*> {
        val result = companyService.analyzeCompany(userId, id, request.userSkills, request.userExperiences)
        return ApiResponse.success(result)
    }

    @PostMapping("/{id}/resume-check")
    fun checkResume(
        @AuthenticationPrincipal userId: String,
        @PathVariable id: Long,
    ): ApiResponse<*> {
        val (result, checkedAt) = companyService.checkResume(userId, id)
        return ApiResponse.success(ResumeCheckResponseDto.from(result, checkedAt))
    }

    @GetMapping("/{id}/activities")
    fun getActivities(
        @AuthenticationPrincipal userId: String,
        @PathVariable id: Long,
    ): ApiResponse<*> {
        return ApiResponse.success(companyService.getActivities(userId, id))
    }
}
