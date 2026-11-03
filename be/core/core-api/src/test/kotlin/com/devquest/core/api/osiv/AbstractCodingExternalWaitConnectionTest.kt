package com.devquest.core.api.osiv

import com.devquest.core.api.measurement.BlockedMeasurement
import com.devquest.core.api.measurement.BlockingGate
import com.devquest.core.api.measurement.HikariProbe
import com.devquest.core.api.measurement.measureWhileBlocked
import com.devquest.core.domain.model.coding.CodingProblemGenerationResult
import com.devquest.core.domain.model.coding.TestCase
import com.devquest.core.domain.port.CodingProblemGeneratorPort
import com.devquest.core.domain.port.Judge0Port
import com.devquest.core.domain.port.Judge0Result
import com.devquest.core.security.JwtProvider
import com.devquest.storage.db.core.CodingProblemEntity
import com.devquest.storage.db.core.CodingProblemRepository
import com.devquest.storage.db.core.CodingSubmissionRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import javax.sql.DataSource

/**
 * B7-21 ⓐ 회귀 — 느린 외부 경계(LLM·Judge0)를 기다리는 동안 웹 요청이 HikariCP 커넥션을 쥐지 않는다.
 *
 * 배경: OSIV(`spring.jpa.open-in-view`, Boot 기본 true)가 켜져 있으면 요청 스레드가 첫 JPA 호출에서 빌린 커넥션을
 * 요청이 끝날 때까지 쥔다 - 코딩 문제 생성(LLM 최대 150초)·제출(Judge0) 동안 풀 슬롯 1개가 통째로 묶인다(D-016 실측: active=1).
 * 앱 설정으로 OSIV 를 껐고(`application.yml`), 이 테스트가 그 결정을 잠근다.
 *
 * 방법: 실제 `DispatcherServlet` → (OSIV 인터셉터가 있다면) → 컨트롤러 → [com.devquest.core.domain.CodingQuestService] →
 * JPA 어댑터 전체 체인을 MockMvc 로 통과시키되, 외부 경계 포트만 [BlockingGate] 로 블록시키는 더블로 바꾼다.
 * 블록된 동안 `HikariPoolMXBean.activeConnections` 를 읽는다.
 *
 * 🔴 **테스트 프로퍼티 오버라이드 없이 기본 설정 그대로** 띄운다 - 서브클래스가 `spring.jpa.open-in-view` 를 주입하면
 * 이 테스트는 "앱 설정이 OSIV 를 끈다" 를 증명하지 못한다(설정 파일에서 그 줄이 지워져도 통과해 버린다).
 *
 * ⚠️ 한계: MockMvc 는 실제 서블릿 컨테이너(Tomcat)가 아니다. OSIV 인터셉터는 `DispatcherServlet` 안에서
 * 돌므로 동일하게 걸리지만(그래서 인터셉터 빈 개수도 함께 단언한다), 커넥션 풀 점유는 Hibernate/Spring 의
 * EntityManager 수명에 달려 있어 서블릿 컨테이너 종류와 무관하다.
 */
abstract class AbstractCodingExternalWaitConnectionTest(
    private val config: String,
) {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var dataSource: DataSource

    @Autowired
    protected lateinit var jwtProvider: JwtProvider

    @Autowired
    protected lateinit var context: ApplicationContext

    @Autowired
    protected lateinit var codingProblemRepository: CodingProblemRepository

    @Autowired
    protected lateinit var codingSubmissionRepository: CodingSubmissionRepository

    @MockitoBean
    protected lateinit var generatorPort: CodingProblemGeneratorPort

    @MockitoBean
    protected lateinit var judge0Port: Judge0Port

    private val userId = "measure-user"
    private lateinit var probe: HikariProbe

    @BeforeEach
    fun cleanState() {
        probe = HikariProbe(dataSource)
        codingSubmissionRepository.deleteAll()
        codingProblemRepository.deleteAll()
    }

    private fun bearer() = "Bearer ${jwtProvider.generate(userId)}"

    private fun generated() = CodingProblemGenerationResult(
        title = "측정용 문제",
        description = "측정용",
        category = "ARRAY",
        solutionCode = "class Solution {}",
        testCases = listOf(TestCase(input = "1", expectedOutput = "1")),
    )

    private fun report(point: String, m: BlockedMeasurement<*>) {
        println(
            "[OSIV-REGRESSION] $point config=$config osivInterceptorBeans=${context.getBeansOfType(OpenEntityManagerInViewInterceptor::class.java).size} " +
                "poolMax=${probe.maximumPoolSize} samples=${m.samples.map { it.active }} last={${m.samples.last()}} status=${m.result}"
        )
    }

    @Test
    fun `OSIV 는 꺼져 있다 - 실효 프로퍼티와 인터셉터 빈이 모두 반영됐다`() {
        assertThat(context.environment.getProperty("spring.jpa.open-in-view"))
            .describedAs("spring.jpa.open-in-view 실효값(어느 yml 에서 왔든 최종 Environment 기준)").isEqualTo("false")
        assertThat(context.getBeansOfType(OpenEntityManagerInViewInterceptor::class.java))
            .describedAs("OSIV 인터셉터 빈").isEmpty()
    }

    @Test
    fun `코딩 문제 생성 - LLM 호출 대기 중에는 커넥션을 쥐지 않는다`() {
        val gate = BlockingGate()
        whenever(generatorPort.generate(any(), any(), any())).thenAnswer {
            gate.block()
            generated()
        }
        whenever(judge0Port.execute(any(), any(), any(), any())).thenReturn(Judge0Result(passed = true))

        val m = measureWhileBlocked(probe, gate) {
            mockMvc.perform(
                get("/api/v1/coding/problem").param("language", "JAVA").param("category", "ARRAY")
                    .header("Authorization", bearer())
            ).andReturn().response.status
        }
        report("generateProblem@LLM", m)

        assertThat(m.result).describedAs("요청은 블록 해제 후 성공해야 한다").isEqualTo(200)
        assertThat(m.samples.map { it.active }).describedAs("LLM 대기 중 active (OSIV OFF 면 어댑터 호출 사이에 커넥션을 반납한다)").containsOnly(0)
        assertThat(codingProblemRepository.count()).describedAs("생성된 문제가 저장돼야 한다").isEqualTo(1)
    }

    @Test
    fun `코딩 문제 생성 - 검증용 Judge0 호출 대기 중에는 커넥션을 쥐지 않는다`() {
        val gate = BlockingGate()
        whenever(generatorPort.generate(any(), any(), any())).thenReturn(generated())
        whenever(judge0Port.execute(any(), any(), any(), any())).thenAnswer {
            gate.block()
            Judge0Result(passed = true)
        }

        val m = measureWhileBlocked(probe, gate) {
            mockMvc.perform(
                get("/api/v1/coding/problem").param("language", "JAVA").param("category", "ARRAY")
                    .header("Authorization", bearer())
            ).andReturn().response.status
        }
        report("generateProblem@Judge0", m)

        assertThat(m.result).isEqualTo(200)
        assertThat(m.samples.map { it.active }).describedAs("생성 검증 Judge0 대기 중 active").containsOnly(0)
    }

    @Test
    fun `코드 제출 - Judge0 호출 대기 중에는 커넥션을 쥐지 않는다`() {
        val problem = codingProblemRepository.save(
            CodingProblemEntity(
                title = "측정용 문제",
                description = "측정용",
                difficulty = "EASY",
                language = "JAVA",
                solutionCode = "class Solution {}",
                testCases = """[{"input":"1","expectedOutput":"1"}]""",
                category = "ARRAY",
            )
        )
        val gate = BlockingGate()
        whenever(judge0Port.execute(any(), any(), any(), any())).thenAnswer {
            gate.block()
            Judge0Result(stdout = "1", passed = true)
        }

        val m = measureWhileBlocked(probe, gate) {
            mockMvc.perform(
                post("/api/v1/coding/submit").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"problemId":${problem.id},"language":"JAVA","userCode":"class Main {}"}""")
                    .header("Authorization", bearer())
            ).andReturn().response.status
        }
        report("submitCode@Judge0", m)

        assertThat(m.result).isEqualTo(200)
        assertThat(m.samples.map { it.active }).describedAs("제출 Judge0 대기 중 active").containsOnly(0)
        assertThat(codingSubmissionRepository.count()).describedAs("제출 기록이 저장돼야 한다").isEqualTo(1)
    }
}
