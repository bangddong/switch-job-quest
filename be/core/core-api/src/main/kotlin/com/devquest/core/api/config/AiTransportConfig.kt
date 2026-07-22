package com.devquest.core.api.config

import com.devquest.core.api.adapter.ai.http.ActClearReportHttpAdapter
import com.devquest.core.api.adapter.ai.http.BlogHttpEvaluator
import com.devquest.core.api.adapter.ai.http.BossPackageHttpEvaluator
import com.devquest.core.api.adapter.ai.http.CodingHintHttpAdapter
import com.devquest.core.api.adapter.ai.http.CodingProblemGeneratorHttpAdapter
import com.devquest.core.api.adapter.ai.http.CompanyFitHttpEvaluator
import com.devquest.core.api.adapter.ai.http.DeveloperClassHttpEvaluator
import com.devquest.core.api.adapter.ai.http.EssayHttpEvaluator
import com.devquest.core.api.adapter.ai.http.InterviewCoachHttpAdapter
import com.devquest.core.api.adapter.ai.http.InterviewHttpEvaluator
import com.devquest.core.api.adapter.ai.http.JdAnalysisHttpEvaluator
import com.devquest.core.api.adapter.ai.http.Judge0HttpAdapter
import com.devquest.core.api.adapter.ai.http.JourneyReportHttpAdapter
import com.devquest.core.api.adapter.ai.http.PersonalityHttpEvaluator
import com.devquest.core.api.adapter.ai.http.ResumeHttpEvaluator
import com.devquest.core.api.adapter.ai.http.SkillAssessmentHttpAdapter
import com.devquest.core.api.adapter.ai.http.SystemDesignHttpEvaluator
import com.devquest.core.api.adapter.ai.http.TechInterviewHttpAdapter
import com.devquest.core.domain.port.ActClearReportPort
import com.devquest.core.domain.port.BlogEvaluatorPort
import com.devquest.core.domain.port.BossPackageEvaluatorPort
import com.devquest.core.domain.port.CodingHintPort
import com.devquest.core.domain.port.CodingProblemGeneratorPort
import com.devquest.core.domain.port.CompanyFitEvaluatorPort
import com.devquest.core.domain.port.DeveloperClassEvaluatorPort
import com.devquest.core.domain.port.EssayEvaluatorPort
import com.devquest.core.domain.port.InterviewCoachPort
import com.devquest.core.domain.port.InterviewEvaluatorPort
import com.devquest.core.domain.port.JdAnalysisEvaluatorPort
import com.devquest.core.domain.port.Judge0Port
import com.devquest.core.domain.port.JourneyReportPort
import com.devquest.core.domain.port.PersonalityEvaluatorPort
import com.devquest.core.domain.port.ResumeEvaluatorPort
import com.devquest.core.domain.port.SkillAssessmentPort
import com.devquest.core.domain.port.SystemDesignEvaluatorPort
import com.devquest.core.domain.port.TechInterviewPort
import tools.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.client.RestClient

/**
 * AI 포트 전송 계층(in-process ↔ HTTP) 전환 스위치.
 *
 * `devquest.ai.transport` 프로퍼티로 AI 평가 포트의 구현체를 선택한다.
 *
 * - 기본값(`inprocess`, 미설정 포함): 이 설정 클래스는 어떤 빈도 만들지 않는다 → `client-ai`의
 *   `@Component` 평가자(예: `TechBlogEvaluator`)가 각 포트의 유일한 구현으로 주입된다.
 *   → 런타임 동작이 완전히 불변이다(Phase 0 무행동 원칙).
 * - `http`: 이 설정 클래스가 HTTP 어댑터 빈을 등록하고 `@Primary`를 부여해 client-ai 빈 대신
 *   주입되게 한다. client-ai 빈은 컨텍스트에 여전히 존재하지만(롤백을 위해) 사용되지는 않는다.
 *
 * `client-ai` 모듈과 소비 서비스(`AiCheckService` 등)는 이 클래스의 존재를 모른다 — 둘 다 포트
 * 인터페이스만 주입받으므로, 전송 계층 전환이 이 설정 파일 안에 국한된다(무수정 보장).
 *
 * 각 `@Bean` 메서드는 [RestClient]·[ObjectMapper]를 **메서드 파라미터**로 받는다(클래스 생성자가
 * 아님) — `RestClient` 빈 자체가 [AiHttpClientConfig]에서 같은 조건(`transport=http`)으로만 등록되므로,
 * inprocess 모드에서는 이 메서드들이 애초에 호출되지 않아 `RestClient` 미존재로 인한 기동 실패가
 * 일어나지 않는다(둘 다 같은 조건이라야 하는 이유).
 *
 * Phase 1 Task 1.4a — 17개 `AiEvaluatorPort` + `Judge0Port` = 18개 포트 전부를 등록한다
 * (Judge0 포함 근거는 [com.devquest.core.api.adapter.ai.http.Judge0HttpAdapter] KDoc 참고).
 */
@Configuration
class AiTransportConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun blogHttpEvaluator(restClient: RestClient, objectMapper: ObjectMapper): BlogEvaluatorPort =
        BlogHttpEvaluator(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun resumeHttpEvaluator(restClient: RestClient, objectMapper: ObjectMapper): ResumeEvaluatorPort =
        ResumeHttpEvaluator(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun essayHttpEvaluator(restClient: RestClient, objectMapper: ObjectMapper): EssayEvaluatorPort =
        EssayHttpEvaluator(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun interviewHttpEvaluator(restClient: RestClient, objectMapper: ObjectMapper): InterviewEvaluatorPort =
        InterviewHttpEvaluator(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun personalityHttpEvaluator(restClient: RestClient, objectMapper: ObjectMapper): PersonalityEvaluatorPort =
        PersonalityHttpEvaluator(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun systemDesignHttpEvaluator(restClient: RestClient, objectMapper: ObjectMapper): SystemDesignEvaluatorPort =
        SystemDesignHttpEvaluator(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun companyFitHttpEvaluator(restClient: RestClient, objectMapper: ObjectMapper): CompanyFitEvaluatorPort =
        CompanyFitHttpEvaluator(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun jdAnalysisHttpEvaluator(restClient: RestClient, objectMapper: ObjectMapper): JdAnalysisEvaluatorPort =
        JdAnalysisHttpEvaluator(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun bossPackageHttpEvaluator(restClient: RestClient, objectMapper: ObjectMapper): BossPackageEvaluatorPort =
        BossPackageHttpEvaluator(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun developerClassHttpEvaluator(restClient: RestClient, objectMapper: ObjectMapper): DeveloperClassEvaluatorPort =
        DeveloperClassHttpEvaluator(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun codingProblemGeneratorHttpAdapter(
        restClient: RestClient,
        objectMapper: ObjectMapper,
    ): CodingProblemGeneratorPort = CodingProblemGeneratorHttpAdapter(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun codingHintHttpAdapter(restClient: RestClient, objectMapper: ObjectMapper): CodingHintPort =
        CodingHintHttpAdapter(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun skillAssessmentHttpAdapter(restClient: RestClient, objectMapper: ObjectMapper): SkillAssessmentPort =
        SkillAssessmentHttpAdapter(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun journeyReportHttpAdapter(restClient: RestClient, objectMapper: ObjectMapper): JourneyReportPort =
        JourneyReportHttpAdapter(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun actClearReportHttpAdapter(restClient: RestClient, objectMapper: ObjectMapper): ActClearReportPort =
        ActClearReportHttpAdapter(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun interviewCoachHttpAdapter(restClient: RestClient, objectMapper: ObjectMapper): InterviewCoachPort =
        InterviewCoachHttpAdapter(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun techInterviewHttpAdapter(restClient: RestClient, objectMapper: ObjectMapper): TechInterviewPort =
        TechInterviewHttpAdapter(restClient, objectMapper)

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai", name = ["transport"], havingValue = "http")
    fun judge0HttpAdapter(restClient: RestClient, objectMapper: ObjectMapper): Judge0Port =
        Judge0HttpAdapter(restClient, objectMapper)
}
