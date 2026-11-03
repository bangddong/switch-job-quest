package com.devquest.core.api.measurement

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
 * D-016 DB 타임아웃 결정 전 실측 — M1·M2 (core-api 코딩 퀘스트).
 *
 * 질문: LLM/Judge0 같은 느린 외부 경계를 기다리는 동안 웹 요청이 HikariCP 커넥션을 쥐는가?
 * 방법: 실제 `DispatcherServlet` → OSIV 인터셉터 → 컨트롤러 → [com.devquest.core.domain.CodingQuestService] →
 * JPA 어댑터 전체 체인을 MockMvc 로 통과시키되, 외부 경계 포트만 [BlockingGate] 로 블록시키는 더블로 바꾼다.
 * 블록된 동안 `HikariPoolMXBean.activeConnections` 를 읽는다.
 *
 * 구성별 서브클래스가 `spring.jpa.open-in-view` 와 DB(H2/Postgres)를 정한다. 기대값은 **실측값**이다 —
 * 아래 숫자가 깨지면 서비스의 트랜잭션 경계나 OSIV 설정이 바뀐 것이므로 결정 근거를 다시 봐야 한다.
 *
 * ⚠️ 한계: MockMvc 는 실제 서블릿 컨테이너(Tomcat)가 아니다. OSIV 인터셉터는 `DispatcherServlet` 안에서
 * 돌므로 동일하게 걸리지만(그래서 [osivInterceptorBeans] 로 빈 존재도 함께 단언한다), 커넥션 풀 점유는
 * Hibernate/Spring 의 EntityManager 수명에 달려 있어 서블릿 컨테이너 종류와 무관하다.
 */
abstract class AbstractCodingOsivMeasurement(
    private val config: String,
    private val osivInterceptorBeans: Int,
    private val activeWhileHeld: Int,
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
            "[MEASURE] $point config=$config osivInterceptorBeans=${context.getBeansOfType(OpenEntityManagerInViewInterceptor::class.java).size} " +
                "poolMax=${probe.maximumPoolSize} samples=${m.samples.map { it.active }} last={${m.samples.last()}} status=${m.result}"
        )
    }

    @Test
    fun `OSIV 인터셉터 빈 존재가 구성과 일치한다 (설정이 컨텍스트에 실제로 반영됐는지)`() {
        assertThat(context.getBeansOfType(OpenEntityManagerInViewInterceptor::class.java)).hasSize(osivInterceptorBeans)
    }

    @Test
    fun `M1a 코딩 문제 생성 - LLM 호출 블록 중 active 커넥션`() {
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
        report("M1a(generateProblem@LLM)", m)

        assertThat(m.result).describedAs("요청은 블록 해제 후 성공해야 한다").isEqualTo(200)
        assertThat(m.samples.map { it.active }).describedAs("LLM 블록 중 active").containsOnly(activeWhileHeld)
        assertThat(codingProblemRepository.count()).describedAs("생성된 문제가 저장돼야 한다").isEqualTo(1)
    }

    @Test
    fun `M1b 코딩 문제 생성 - 생성 직후 검증용 Judge0 호출 블록 중 active 커넥션`() {
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
        report("M1b(generateProblem@Judge0)", m)

        assertThat(m.result).isEqualTo(200)
        assertThat(m.samples.map { it.active }).describedAs("생성 검증 Judge0 블록 중 active").containsOnly(activeWhileHeld)
    }

    @Test
    fun `M2 코드 제출 - Judge0 호출 블록 중 active 커넥션`() {
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
        report("M2(submitCode@Judge0)", m)

        assertThat(m.result).isEqualTo(200)
        assertThat(m.samples.map { it.active }).describedAs("제출 Judge0 블록 중 active").containsOnly(activeWhileHeld)
        assertThat(codingSubmissionRepository.count()).describedAs("제출 기록이 저장돼야 한다").isEqualTo(1)
    }
}
