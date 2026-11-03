package com.devquest.core.api.controller.v1

import com.devquest.storage.db.core.DailyQuestionContentRepository
import com.devquest.storage.db.core.TechQuestionBankEntity
import com.devquest.storage.db.core.TechQuestionBankRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Phase 2 Stage A — ⚠️ 주의사항 ② 웹 스레드 읽기 경로 통합 검증 (+ B7-21 ⓐ 로 OSIV OFF 에서도 같은 결과임을 고정).
 *
 * 이 테스트가 처음 쓰였을 때는 `spring.jpa.open-in-view` 가 be 전체 설정에 0건이라 Boot 기본값(`true`)이 적용됐고, 웹 요청 스레드가
 * `OpenEntityManagerInViewInterceptor` 가 바인딩한 EntityManager 위에서 실행됐다 - 그 상태를 순수 Mockito 인
 * `DailyQuestionContentServiceTest` 가 재현하지 못해서 만든 테스트다. **B7-21 ⓐ 에서 OSIV 를 껐다**(`application.yml`). 테스트는 그대로
 * 통과했다 - 즉 이 읽기 경로의 findToday → findQuestionsSince → save 체인은 OSIV 바인딩에 기대지 않고 어댑터 호출마다
 * (Spring Data 의 메서드별 트랜잭션으로) 스스로 완결된다. 클래스 이름의 "Osiv" 는 이 테스트의 유래이고, 지금 OSIV 상태를 단언하지는
 * 않는다(그건 `CodingExternalWaitConnectionTest` 의 몫).
 *
 * 실제 `DispatcherServlet` → 컨트롤러 → 서비스 → JPA 어댑터 전체 체인을 `@SpringBootTest(webEnvironment = MOCK)` + `MockMvc` 로 통과시켜,
 * "읽기 경로(웹 스레드)에서 처음 실행되는 저장/UNIQUE 복구 경로"가 실제로 동작하는지 확인한다.
 *
 * ⚠️ 한계(정직하게 기록): 여기서 쓰는 DB는 `db-core.yml`의 H2 in-memory
 * (`ddl-auto: create-drop`)다 — `FlywayMigrationIntegrationTest`처럼 진짜 Postgres를 쓰지 않는다.
 * "동시 요청 두 개가 동시에 `save()`해 UNIQUE 위반이 실제로 발생하는" 경합 시나리오까지는 여기서
 * 재현하지 못한다(단일 요청 순차 실행). 그 경합 로직 자체(예외를 잡아 재조회)는
 * `DailyQuestionContentServiceTest`의 Mockito 테스트가 커버한다 — 이 테스트가 추가로 증명하는 것은
 * "웹 스레드에서 findToday → findQuestionsSince → save 체인이 예외 없이 완주한다"는 부분이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DailyQuestionOsivIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var dailyQuestionContentRepository: DailyQuestionContentRepository

    @Autowired
    private lateinit var techQuestionBankRepository: TechQuestionBankRepository

    @BeforeEach
    fun cleanState() {
        dailyQuestionContentRepository.deleteAll()
        techQuestionBankRepository.deleteAll()
    }

    @Test
    fun `오늘 행이 없고 뱅크에 후보가 있으면 웹 스레드에서 200과 함께 뱅크 질문이 저장된다`() {
        techQuestionBankRepository.save(
            TechQuestionBankEntity(category = "java-spring", question = "OSIV 통합 테스트용 뱅크 질문"),
        )

        mockMvc.perform(get("/api/v1/daily-question"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.question").value("OSIV 통합 테스트용 뱅크 질문"))

        val saved = dailyQuestionContentRepository.findAll()
        assertThat(saved).hasSize(1)
        assertThat(saved.first().source).isEqualTo("BANK")
    }

    @Test
    fun `오늘 행이 없고 뱅크도 소진이면 웹 스레드에서도 404를 반환하고 아무것도 저장하지 않는다 (AI 미호출)`() {
        // 뱅크를 비워둔다 — 만약 읽기 경로가 실수로 ensureTodayQuestion()(AI 폴백 포함)을 호출하게 되면
        // test 프로파일에는 실제 AI 자격 증명이 없어 이 요청이 예외/타임아웃으로 404가 아닌 다른
        // 결과가 나온다. 즉 이 테스트는 "AI를 절대 호출하지 않는다"를 목(mock) 없이 행동으로 증명한다.
        mockMvc.perform(get("/api/v1/daily-question"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("DAILY_QUESTION_NOT_FOUND"))

        assertThat(dailyQuestionContentRepository.findAll()).isEmpty()
    }

    @Test
    fun `같은 날 두 번 요청해도 두 번째는 재생성 없이 동일한 질문을 반환한다 (멱등, 웹 스레드)`() {
        techQuestionBankRepository.save(
            TechQuestionBankEntity(category = "java-spring", question = "멱등성 검증용 뱅크 질문"),
        )

        mockMvc.perform(get("/api/v1/daily-question"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.question").value("멱등성 검증용 뱅크 질문"))

        mockMvc.perform(get("/api/v1/daily-question"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.question").value("멱등성 검증용 뱅크 질문"))

        assertThat(dailyQuestionContentRepository.findAll()).hasSize(1)
    }
}
