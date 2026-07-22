package com.devquest.core.domain

import com.devquest.core.domain.model.ActivityType
import com.devquest.core.domain.model.AppliedCompany
import com.devquest.core.domain.model.ApplicationStatus
import com.devquest.core.domain.model.CompanyActivity
import com.devquest.core.domain.model.evaluation.JdAnalysisResult
import com.devquest.core.domain.model.evaluation.ResumeCheckResult
import com.devquest.core.domain.port.CompanyActivityPort
import com.devquest.core.domain.port.CompanyPort
import com.devquest.core.domain.port.JdAnalysisEvaluatorPort
import com.devquest.core.domain.port.ResumeEvaluatorPort
import com.devquest.core.domain.port.UserResumePort
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
    private val resumeEvaluatorPort: ResumeEvaluatorPort,
    private val userResumePort: UserResumePort,
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

        val fromStatus = existing.status
        val updated = existing.copy(
            status = status,
            appliedAt = appliedAt ?: existing.appliedAt,
        )
        val saved = companyPort.save(updated)

        if (fromStatus != status) {
            val json = objectMapper.writeValueAsString(mapOf("from" to fromStatus.name, "to" to status.name))
            companyActivityPort.save(
                CompanyActivity(
                    companyId = companyId,
                    userId = userId,
                    activityType = ActivityType.STATUS_CHANGE,
                    aiScore = 0,
                    aiResultJson = json,
                )
            )
        }

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

    /**
     * ⚠️ **트랜잭션 경계 (Phase 1 Task 1.4b/1.5)**: 의도적으로 `@Transactional`을 붙이지 않는다.
     * 회사·이력서 조회(읽기)와 활동 로그 저장(쓰기)은 각각 [CompanyPort]·[UserResumePort]·
     * [CompanyActivityPort] 구현체(db-core의 Spring Data JPA 리포지토리 어댑터)가 호출 시점마다
     * 자체적으로 트랜잭션을 여는 개별 CRUD 호출이라 이 메서드가 별도로 감쌀 필요가 없다(Spring Data
     * JPA의 `SimpleJpaRepository`는 `save`/조회 메서드마다 자체 `@Transactional`을 갖는다 — 실측:
     * `CompanyActivityAdapter.save`가 `JpaRepository.save`에 그대로 위임). AI 호출(`jdAnalysisEvaluatorPort
     * .analyze`) 동안 DB 커넥션을 붙잡지 않기 위해 예전의 메서드 전체 `@Transactional`을 제거했다 —
     * 자세한 근거는 [AiCheckService] 상단 KDoc 참고(동일 패턴).
     */
    fun analyzeCompany(
        userId: String,
        companyId: Long,
        userSkills: List<String> = emptyList(),
        userExperiences: List<String> = emptyList(),
    ): JdAnalysisResult {
        val company = companyPort.findByIdAndUserId(companyId, userId)
            ?: throw CoreException(ErrorType.COMPANY_NOT_FOUND)
        val jd = company.jobDescription ?: throw CoreException(ErrorType.INVALID_REQUEST)

        val result = if (userSkills.isEmpty() && userExperiences.isEmpty()) {
            val resume = userResumePort.findByUserId(userId)
                ?: throw CoreException(ErrorType.RESUME_NOT_REGISTERED)
            jdAnalysisEvaluatorPort.analyze(company.companyName, jd, emptyList(), emptyList(), resume.content)
        } else {
            jdAnalysisEvaluatorPort.analyze(company.companyName, jd, userSkills, userExperiences, "")
        }

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

    /** ⚠️ 트랜잭션 경계 — [analyzeCompany] KDoc과 동일 근거로 `@Transactional`을 의도적으로 뺐다. */
    fun checkResume(userId: String, companyId: Long): Pair<ResumeCheckResult, LocalDateTime> {
        val company = companyPort.findByIdAndUserId(companyId, userId)
            ?: throw CoreException(ErrorType.COMPANY_NOT_FOUND)
        val jd = company.jobDescription?.takeIf { it.isNotBlank() }
            ?: throw CoreException(ErrorType.COMPANY_JD_NOT_REGISTERED)
        val resume = userResumePort.findByUserId(userId)
            ?: throw CoreException(ErrorType.RESUME_NOT_REGISTERED)

        val result = resumeEvaluatorPort.evaluate(company.companyName, jd, resume.content)
        val json = objectMapper.writeValueAsString(result)
        val saved = companyActivityPort.save(
            CompanyActivity(
                companyId = companyId,
                userId = userId,
                activityType = ActivityType.RESUME_CHECK,
                aiScore = result.overallScore,
                aiResultJson = json,
            )
        )
        log.info("이력서 점검 완료: userId=${userId}, companyId=${companyId}, score=${result.overallScore}")
        return result to saved.createdAt
    }

    fun getActivities(userId: String, companyId: Long): List<CompanyActivity> {
        companyPort.findByIdAndUserId(companyId, userId)
            ?: throw CoreException(ErrorType.COMPANY_NOT_FOUND)
        return companyActivityPort.findAllByCompanyId(companyId)
    }
}
