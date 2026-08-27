package com.devquest.daily

import com.devquest.storage.db.core.DailyQuestionContentRepository
import com.devquest.storage.db.core.TechQuestionBankEntity
import com.devquest.storage.db.core.TechQuestionBankRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Phase 2 Stage B-2b 완료 기준 — daily-api를 **실제로 기동**해 `GET /api/v1/daily-question`이
 * ai-api 없이 200을 주는지 검증한다(함정 ⑨). 컨텍스트 로드 테스트만으로는 부족하다는 지시에 따라
 * `@SpringBootTest(webEnvironment = RANDOM_PORT)`로 실제 내장 서버를 띄운다.
 *
 * ⚠️ Deviation: 지시가 예시로 든 `TestRestTemplate`은 이 레포의 Spring Boot 4.0.3 / Spring
 * Framework 7 조합에서 제거되었다(실측: 전체 `.gradle` 캐시에 `TestRestTemplate.class`가 0건 —
 * 후속 `org.springframework.test.web.servlet.client.RestTestClient`로 대체됐지만, `@LocalServerPort`에
 * 자동 바인딩되는 Boot 자동구성을 찾지 못했다). 외부 라이브러리 추측 대신 JDK 표준
 * `java.net.http.HttpClient`로 직접 호출한다 — 추가 의존성 없이 동일한 검증 목적(실제 내장 서버가
 * 실제 HTTP 요청에 응답하는지)을 달성한다.
 *
 * 외부 프로세스(ai-api)는 기동하지 않는다 — 뱅크 전용 조회 경로는 AI를 호출하지 않는다.
 * 뱅크 시드는 core-api `DailyQuestionOsivIntegrationTest`와 동일 패턴 — db-core의
 * `TechQuestionBankRepository`를 직접 주입받아 저장한다(daily-api 기본 프로필은 H2
 * `ddl-auto: create-drop`이라 Flyway 시드 데이터가 없다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DailyApiStandaloneBootTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var dailyQuestionContentRepository: DailyQuestionContentRepository

    @Autowired
    private lateinit var techQuestionBankRepository: TechQuestionBankRepository

    private val httpClient = HttpClient.newHttpClient()

    @BeforeEach
    fun cleanState() {
        dailyQuestionContentRepository.deleteAll()
        techQuestionBankRepository.deleteAll()
    }

    private fun getDailyQuestion(): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/api/v1/daily-question"))
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `실제 내장 서버 기동 후 GET daily-question이 ai-api 없이 200을 반환한다`() {
        techQuestionBankRepository.save(
            TechQuestionBankEntity(category = "java-spring", question = "단독 기동 검증용 뱅크 질문")
        )

        val response = getDailyQuestion()

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).contains("단독 기동 검증용 뱅크 질문")
    }

    @Test
    fun `뱅크가 비어 있으면 404를 반환한다`() {
        val response = getDailyQuestion()

        assertThat(response.statusCode()).isEqualTo(404)
        assertThat(response.body()).contains("DAILY_QUESTION_NOT_FOUND")
    }
}
