package com.devquest.core.api.config

import com.devquest.client.ai.evaluator.ActClearReportEvaluator
import com.devquest.client.ai.evaluator.BossPackageEvaluator
import com.devquest.client.ai.evaluator.CareerEssayEvaluator
import com.devquest.client.ai.evaluator.CodingHintEvaluator
import com.devquest.client.ai.evaluator.CodingProblemGeneratorEvaluator
import com.devquest.client.ai.evaluator.CompanyFitEvaluator
import com.devquest.client.ai.evaluator.DeveloperClassEvaluator
import com.devquest.client.ai.evaluator.InterviewCoachEvaluator
import com.devquest.client.ai.evaluator.JdAnalysisEvaluator
import com.devquest.client.ai.evaluator.JourneyReportGenerator
import com.devquest.client.ai.evaluator.MockInterviewEvaluator
import com.devquest.client.ai.evaluator.PersonalityInterviewEvaluator
import com.devquest.client.ai.evaluator.ResumeCheckEvaluator
import com.devquest.client.ai.evaluator.SkillAssessmentEvaluator
import com.devquest.client.ai.evaluator.SystemDesignEvaluator
import com.devquest.client.ai.evaluator.TechBlogEvaluator
import com.devquest.client.ai.evaluator.TechInterviewEvaluator
import com.devquest.client.ai.judge0.Judge0Adapter
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient

/**
 * 서비스 분해 Phase 0 Task 0.4 → Phase 1 Task 1.4a — `devquest.ai.transport` 전환 스위치 회귀 테스트.
 *
 * 소비 서비스(AiCheckService 등)는 포트 인터페이스만 주입받으므로, 실제 주입되는 구현체가 transport
 * 값에 따라 바뀌는지를 **18개 포트 전부**(17개 `AiEvaluatorPort` + `Judge0Port`)로 증명한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AiTransportInprocessSwitchTest {

    @Autowired
    private lateinit var blogEvaluatorPort: BlogEvaluatorPort

    @Autowired
    private lateinit var resumeEvaluatorPort: ResumeEvaluatorPort

    @Autowired
    private lateinit var essayEvaluatorPort: EssayEvaluatorPort

    @Autowired
    private lateinit var interviewEvaluatorPort: InterviewEvaluatorPort

    @Autowired
    private lateinit var personalityEvaluatorPort: PersonalityEvaluatorPort

    @Autowired
    private lateinit var systemDesignEvaluatorPort: SystemDesignEvaluatorPort

    @Autowired
    private lateinit var companyFitEvaluatorPort: CompanyFitEvaluatorPort

    @Autowired
    private lateinit var jdAnalysisEvaluatorPort: JdAnalysisEvaluatorPort

    @Autowired
    private lateinit var bossPackageEvaluatorPort: BossPackageEvaluatorPort

    @Autowired
    private lateinit var developerClassEvaluatorPort: DeveloperClassEvaluatorPort

    @Autowired
    private lateinit var codingProblemGeneratorPort: CodingProblemGeneratorPort

    @Autowired
    private lateinit var codingHintPort: CodingHintPort

    @Autowired
    private lateinit var skillAssessmentPort: SkillAssessmentPort

    @Autowired
    private lateinit var journeyReportPort: JourneyReportPort

    @Autowired
    private lateinit var actClearReportPort: ActClearReportPort

    @Autowired
    private lateinit var interviewCoachPort: InterviewCoachPort

    @Autowired
    private lateinit var techInterviewPort: TechInterviewPort

    @Autowired
    private lateinit var judge0Port: Judge0Port

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    /**
     * 무행동(no-op) 증거 — HTTP 어댑터가 "주입되지 않음"을 넘어 컨텍스트에 **아예 등록조차 안 됨**을
     * 직접 증명한다. `RestClient`(및 그걸 의존하는 18개 HTTP 어댑터 빈)는 `AiHttpClientConfig`·
     * `AiTransportConfig`가 전부 같은 `@ConditionalOnProperty(transport=http)` 조건이라, inprocess
     * (기본값)에서는 이 빈 자체가 만들어지지 않는다 — 즉 실행 경로에 진입할 방법이 없다.
     */
    @Test
    fun `devquest ai transport 미설정 시 RestClient 빈 자체가 등록되지 않는다 (무행동 증거)`() {
        assertThat(applicationContext.getBeanNamesForType(RestClient::class.java)).isEmpty()
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - blog`() {
        assertThat(blogEvaluatorPort).isInstanceOf(TechBlogEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - resume`() {
        assertThat(resumeEvaluatorPort).isInstanceOf(ResumeCheckEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - essay`() {
        assertThat(essayEvaluatorPort).isInstanceOf(CareerEssayEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - interview`() {
        assertThat(interviewEvaluatorPort).isInstanceOf(MockInterviewEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - personality`() {
        assertThat(personalityEvaluatorPort).isInstanceOf(PersonalityInterviewEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - system-design`() {
        assertThat(systemDesignEvaluatorPort).isInstanceOf(SystemDesignEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - company-fit`() {
        assertThat(companyFitEvaluatorPort).isInstanceOf(CompanyFitEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - jd-analysis`() {
        assertThat(jdAnalysisEvaluatorPort).isInstanceOf(JdAnalysisEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - boss-package`() {
        assertThat(bossPackageEvaluatorPort).isInstanceOf(BossPackageEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - developer-class`() {
        assertThat(developerClassEvaluatorPort).isInstanceOf(DeveloperClassEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - coding-problem`() {
        assertThat(codingProblemGeneratorPort).isInstanceOf(CodingProblemGeneratorEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - coding-hint`() {
        assertThat(codingHintPort).isInstanceOf(CodingHintEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - skill-assessment`() {
        assertThat(skillAssessmentPort).isInstanceOf(SkillAssessmentEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - journey-report`() {
        assertThat(journeyReportPort).isInstanceOf(JourneyReportGenerator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - act-clear-report`() {
        assertThat(actClearReportPort).isInstanceOf(ActClearReportEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - interview-coach`() {
        assertThat(interviewCoachPort).isInstanceOf(InterviewCoachEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - tech-interview`() {
        assertThat(techInterviewPort).isInstanceOf(TechInterviewEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다 - judge0`() {
        assertThat(judge0Port).isInstanceOf(Judge0Adapter::class.java)
    }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["devquest.ai.transport=http"],
)
@ActiveProfiles("test")
class AiTransportHttpSwitchTest {

    @Autowired
    private lateinit var blogEvaluatorPort: BlogEvaluatorPort

    @Autowired
    private lateinit var resumeEvaluatorPort: ResumeEvaluatorPort

    @Autowired
    private lateinit var essayEvaluatorPort: EssayEvaluatorPort

    @Autowired
    private lateinit var interviewEvaluatorPort: InterviewEvaluatorPort

    @Autowired
    private lateinit var personalityEvaluatorPort: PersonalityEvaluatorPort

    @Autowired
    private lateinit var systemDesignEvaluatorPort: SystemDesignEvaluatorPort

    @Autowired
    private lateinit var companyFitEvaluatorPort: CompanyFitEvaluatorPort

    @Autowired
    private lateinit var jdAnalysisEvaluatorPort: JdAnalysisEvaluatorPort

    @Autowired
    private lateinit var bossPackageEvaluatorPort: BossPackageEvaluatorPort

    @Autowired
    private lateinit var developerClassEvaluatorPort: DeveloperClassEvaluatorPort

    @Autowired
    private lateinit var codingProblemGeneratorPort: CodingProblemGeneratorPort

    @Autowired
    private lateinit var codingHintPort: CodingHintPort

    @Autowired
    private lateinit var skillAssessmentPort: SkillAssessmentPort

    @Autowired
    private lateinit var journeyReportPort: JourneyReportPort

    @Autowired
    private lateinit var actClearReportPort: ActClearReportPort

    @Autowired
    private lateinit var interviewCoachPort: InterviewCoachPort

    @Autowired
    private lateinit var techInterviewPort: TechInterviewPort

    @Autowired
    private lateinit var judge0Port: Judge0Port

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `devquest ai transport=http 시 RestClient 빈이 등록된다 (inprocess 무행동 증거의 대조군)`() {
        assertThat(applicationContext.getBeanNamesForType(RestClient::class.java)).hasSize(1)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - blog`() {
        assertThat(blogEvaluatorPort).isInstanceOf(BlogHttpEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - resume`() {
        assertThat(resumeEvaluatorPort).isInstanceOf(ResumeHttpEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - essay`() {
        assertThat(essayEvaluatorPort).isInstanceOf(EssayHttpEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - interview`() {
        assertThat(interviewEvaluatorPort).isInstanceOf(InterviewHttpEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - personality`() {
        assertThat(personalityEvaluatorPort).isInstanceOf(PersonalityHttpEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - system-design`() {
        assertThat(systemDesignEvaluatorPort).isInstanceOf(SystemDesignHttpEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - company-fit`() {
        assertThat(companyFitEvaluatorPort).isInstanceOf(CompanyFitHttpEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - jd-analysis`() {
        assertThat(jdAnalysisEvaluatorPort).isInstanceOf(JdAnalysisHttpEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - boss-package`() {
        assertThat(bossPackageEvaluatorPort).isInstanceOf(BossPackageHttpEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - developer-class`() {
        assertThat(developerClassEvaluatorPort).isInstanceOf(DeveloperClassHttpEvaluator::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - coding-problem`() {
        assertThat(codingProblemGeneratorPort).isInstanceOf(CodingProblemGeneratorHttpAdapter::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - coding-hint`() {
        assertThat(codingHintPort).isInstanceOf(CodingHintHttpAdapter::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - skill-assessment`() {
        assertThat(skillAssessmentPort).isInstanceOf(SkillAssessmentHttpAdapter::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - journey-report`() {
        assertThat(journeyReportPort).isInstanceOf(JourneyReportHttpAdapter::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - act-clear-report`() {
        assertThat(actClearReportPort).isInstanceOf(ActClearReportHttpAdapter::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - interview-coach`() {
        assertThat(interviewCoachPort).isInstanceOf(InterviewCoachHttpAdapter::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - tech-interview`() {
        assertThat(techInterviewPort).isInstanceOf(TechInterviewHttpAdapter::class.java)
    }

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다 - judge0`() {
        assertThat(judge0Port).isInstanceOf(Judge0HttpAdapter::class.java)
    }
}
