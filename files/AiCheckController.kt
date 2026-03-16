package com.devquest.controller

import com.devquest.model.request.*
import com.devquest.model.response.*
import com.devquest.service.AiCheckService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * DevQuest AI Check REST API
 *
 * Base URL: /api/v1/ai-check
 * 각 퀘스트 타입별 AI 검사 엔드포인트
 */
@RestController
@RequestMapping("/api/v1/ai-check")
class AiCheckController(
    private val aiCheckService: AiCheckService
) {

    // ── Act I ───────────────────────────────────────────────────

    /**
     * [Quest 1-2] 이직 동기 에세이 검사
     * 불만족 사항, 목표, 비전을 분석해 이직 동기의 명확성 평가
     */
    @PostMapping("/career-essay")
    fun checkCareerEssay(@Valid @RequestBody request: CareerEssayCheckRequest): ResponseEntity<ApiResponse<EssayCheckResult>> {
        return ResponseEntity.ok(aiCheckService.checkCareerEssay(request))
    }

    /**
     * [Boss 1-BOSS] 회사 핏 종합 분석
     * 선호도 설문 + 회사 정보를 기반으로 핏 점수 계산
     */
    @PostMapping("/company-fit")
    fun analyzeCompanyFit(@Valid @RequestBody request: CompanyFitRequest): ResponseEntity<ApiResponse<List<CompanyFitResult>>> {
        return ResponseEntity.ok(aiCheckService.analyzeCompanyFit(request))
    }

    // ── Act II ──────────────────────────────────────────────────

    /**
     * [Quest 2-2] 기술 블로그 정확성 검사
     * 블로그 본문의 기술적 정확성, 깊이, 코드 품질 평가
     */
    @PostMapping("/tech-blog")
    fun checkTechBlog(@Valid @RequestBody request: TechBlogCheckRequest): ResponseEntity<ApiResponse<AiEvaluationResult>> {
        return ResponseEntity.ok(aiCheckService.checkTechBlog(request))
    }

    /**
     * [Quest 2-3] 시스템 설계 평가
     * 아키텍처 설명과 고려 사항을 평가
     */
    @PostMapping("/system-design")
    fun checkSystemDesign(@Valid @RequestBody request: SystemDesignCheckRequest): ResponseEntity<ApiResponse<AiEvaluationResult>> {
        return ResponseEntity.ok(aiCheckService.checkSystemDesign(request))
    }

    /**
     * [Boss 2-BOSS] 모의 기술 면접 - 단일 질문 채점
     * 실제 면접 질문에 대한 답변 평가 (4개 기준으로 채점)
     */
    @PostMapping("/mock-interview")
    fun checkMockInterview(@Valid @RequestBody request: MockInterviewRequest): ResponseEntity<ApiResponse<InterviewEvaluationResult>> {
        return ResponseEntity.ok(aiCheckService.checkMockInterview(request))
    }

    /**
     * [Boss 2-BOSS] 면접 질문 생성
     * 카테고리별 면접 질문 랜덤 출제
     */
    @GetMapping("/mock-interview/questions")
    fun generateInterviewQuestions(
        @RequestParam(defaultValue = "DB,JVM,네트워크,운영체제,설계") categories: String,
        @RequestParam(defaultValue = "10") count: Int
    ): ResponseEntity<ApiResponse<List<Map<String, String>>>> {
        val categoryList = categories.split(",").map { it.trim() }
        return ResponseEntity.ok(aiCheckService.generateInterviewQuestions(categoryList, count))
    }

    // ── Act III ─────────────────────────────────────────────────

    /**
     * [Quest 3-2] JD 분석 및 기술 갭 분석
     * 채용공고를 분석해 숨겨진 요구사항과 매칭도 파악
     */
    @PostMapping("/jd-analysis")
    fun analyzeJd(@Valid @RequestBody request: JdAnalysisRequest): ResponseEntity<ApiResponse<JdAnalysisResult>> {
        return ResponseEntity.ok(aiCheckService.analyzeJd(request))
    }

    // ── Act IV ──────────────────────────────────────────────────

    /**
     * [Quest 4-1] 이력서 STAR 기법 검토
     * STAR 기법 활용도, 수치화, JD 키워드 매칭 평가 + 개선 예시 제공
     */
    @PostMapping("/resume")
    fun checkResume(@Valid @RequestBody request: ResumeCheckRequest): ResponseEntity<ApiResponse<ResumeCheckResult>> {
        return ResponseEntity.ok(aiCheckService.checkResume(request))
    }

    // ── Act V ───────────────────────────────────────────────────

    /**
     * [Quest 5-1] 인성 면접 답변 평가
     * 구체성, 진정성, 성장 마인드셋 평가
     */
    @PostMapping("/personality-interview")
    fun checkPersonalityInterview(@Valid @RequestBody request: PersonalityInterviewRequest): ResponseEntity<ApiResponse<AiEvaluationResult>> {
        return ResponseEntity.ok(aiCheckService.checkPersonalityInterview(request))
    }
}

/**
 * 퀘스트 진행 상황 관리 API
 */
@RestController
@RequestMapping("/api/v1/progress")
class ProgressController(
    private val aiCheckService: AiCheckService
) {

    /**
     * 전체 진행 상황 조회 (XP, 완료 퀘스트, 레벨)
     */
    @GetMapping("/{userId}")
    fun getProgress(@PathVariable userId: String): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return ResponseEntity.ok(aiCheckService.getProgress(userId))
    }
}
