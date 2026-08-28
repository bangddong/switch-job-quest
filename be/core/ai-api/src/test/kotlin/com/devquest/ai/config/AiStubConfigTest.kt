package com.devquest.ai.config

import com.devquest.ai.stub.TechInterviewStubEvaluator
import com.devquest.client.ai.evaluator.TechInterviewEvaluator
import com.devquest.core.domain.port.TechInterviewPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/**
 * D-008 — 학습 클러스터 전용 `TechInterviewPort` 스텁의 prod 안전성 계약.
 *
 * 🔴 가장 중요한 것은 "기본값 off" 테스트다 — 스텁 프로퍼티를 켜지 않으면 `client-ai`의 실제
 * 구현([TechInterviewEvaluator])이 그대로 주입돼야 한다. B-2b에서 겪은 함정(`@ConditionalOnProperty`
 * 게이트가 닫혔을 때 폴백이 없어 기동 실패)을 되풀이하지 않는지도 이 테스트가 함께 증명한다 —
 * 여기서는 ai-api가 client-ai를 여전히 의존하므로 폴백 빈이 항상 존재한다.
 */
@SpringBootTest
class AiStubConfigOffByDefaultTest {

    @Autowired
    private lateinit var techInterviewPort: TechInterviewPort

    @Test
    fun `스텁 프로퍼티를 켜지 않으면 client-ai의 실제 구현이 주입된다`() {
        assertThat(techInterviewPort).isInstanceOf(TechInterviewEvaluator::class.java)
        assertThat(techInterviewPort).isNotInstanceOf(TechInterviewStubEvaluator::class.java)
    }
}

@SpringBootTest(properties = ["devquest.ai.stub.tech-interview.enabled=true"])
class AiStubConfigEnabledTest {

    @Autowired
    private lateinit var techInterviewPort: TechInterviewPort

    @Test
    fun `스텁 프로퍼티를 켜면 스텁 구현이 주입되고 응답에 스텁 표식이 들어있다`() {
        assertThat(techInterviewPort).isInstanceOf(TechInterviewStubEvaluator::class.java)

        val dailyQuestion = techInterviewPort.generateDailyQuestion("Kotlin", emptyList())
        val explanation = techInterviewPort.explainFollowup("Q", "A", "F", "UQ", null)

        assertThat(dailyQuestion).contains("[STUB]")
        assertThat(explanation).contains("[STUB]")
    }
}

/**
 * wire 레벨 확인 — 스텁이 켜진 상태에서 실제 내장 서버를 통해 `/explain-followup`을 호출해도
 * `TechInterviewWireFormatContractTest`가 고정한 `text/plain;charset=UTF-8` 계약이 깨지지 않고,
 * 응답 바디에 스텁 표식이 그대로 실려 나오는지 확인한다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["devquest.ai.stub.tech-interview.enabled=true"],
)
class AiStubWireFormatTest {

    @LocalServerPort
    private var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `explain-followup - 스텁이 켜지면 text-plain 계약을 유지한 채 스텁 표식이 담긴 응답을 돌려준다`() {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/internal/ai/tech-interview/explain-followup"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """{"question":"질문","answer":"답변","feedback":"피드백","userQuestion":"추가질문"}""",
                    StandardCharsets.UTF_8,
                )
            )
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.headers().firstValue("Content-Type").orElse(null))
            .isEqualTo("text/plain;charset=UTF-8")
        assertThat(response.body()).contains("[STUB]")
    }
}
