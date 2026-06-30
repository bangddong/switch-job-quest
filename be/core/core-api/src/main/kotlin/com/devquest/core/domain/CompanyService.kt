package com.devquest.core.domain

import com.devquest.core.domain.model.ActivityType
import com.devquest.core.domain.model.AppliedCompany
import com.devquest.core.domain.model.ApplicationStatus
import com.devquest.core.domain.model.CompanyActivity
import com.devquest.core.domain.model.evaluation.JdAnalysisResult
import com.devquest.core.domain.port.CompanyActivityPort
import com.devquest.core.domain.port.CompanyPort
import com.devquest.core.domain.port.JdAnalysisEvaluatorPort
import com.devquest.core.support.error.CoreException
import com.devquest.core.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime

@Service
class CompanyService(
    private val companyPort: CompanyPort,
    private val jdAnalysisEvaluatorPort: JdAnalysisEvaluatorPort,
    private val companyActivityPort: CompanyActivityPort,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createCompany(
        userId: String,
        companyName: String,
        position: String,
        jdUrl: String?,
        jobDescription: String? = null,
    ): AppliedCompany {
        val company = AppliedCompany(
            userId = userId,
            companyName = companyName,
            position = position,
            jdUrl = jdUrl,
            jobDescription = jobDescription,
        )
        val saved = companyPort.save(company)
        log.info("회사 생성: userId=${userId}, companyName=${companyName}")
        return saved
    }

    fun getCompanies(userId: String): List<AppliedCompany> {
        return companyPort.findAllByUserId(userId)
    }

    @Transactional
    fun updateStatus(
        userId: String,
        companyId: Long,
        status: ApplicationStatus,
        appliedAt: LocalDateTime?,
    ): AppliedCompany {
        val existing = companyPort.findByIdAndUserId(companyId, userId)
            ?: throw CoreException(ErrorType.COMPANY_NOT_FOUND)

        val updated = existing.copy(
            status = status,
            appliedAt = appliedAt ?: existing.appliedAt,
        )
        val saved = companyPort.save(updated)
        log.info("회사 상태 변경: userId=${userId}, companyId=${companyId}, status=${status}")
        return saved
    }

    @Transactional
    fun deleteCompany(userId: String, companyId: Long) {
        companyPort.findByIdAndUserId(companyId, userId)
            ?: throw CoreException(ErrorType.COMPANY_NOT_FOUND)

        companyPort.delete(companyId, userId)
        log.info("회사 삭제: userId=${userId}, companyId=${companyId}")
    }

    @Transactional
    fun analyzeCompany(
        userId: String,
        companyId: Long,
        userSkills: List<String>,
        userExperiences: List<String>,
    ): JdAnalysisResult {
        val company = companyPort.findByIdAndUserId(companyId, userId)
            ?: throw CoreException(ErrorType.COMPANY_NOT_FOUND)
        val jd = company.jobDescription ?: throw CoreException(ErrorType.INVALID_REQUEST)
        val result = jdAnalysisEvaluatorPort.analyze(company.companyName, jd, userSkills, userExperiences)
        val json = objectMapper.writeValueAsString(result)
        companyActivityPort.save(
            CompanyActivity(
                companyId = companyId,
                userId = userId,
                activityType = ActivityType.JD_ANALYSIS,
                aiScore = result.overallMatchScore,
                aiResultJson = json,
            )
        )
        log.info("JD 분석 완료: userId=${userId}, companyId=${companyId}, score=${result.overallMatchScore}")
        return result
    }
}
