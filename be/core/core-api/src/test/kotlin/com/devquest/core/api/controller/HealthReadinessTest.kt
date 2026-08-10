package com.devquest.core.api.controller

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.sql.DataSource

/**
 * L-15 — readiness probe가 실제로 아무것도 검증하지 않던 문제 회귀 테스트.
 *
 * "200을 준다"만 확인하면 원래 `/health`(상수 응답)도 통과하므로 이 버그를 못 잡는다.
 * 그래서 아래 테스트들은 반드시 상태를 조작해(DB를 죽이거나, mail을 죽여) 반증 가능한 방식으로
 * 검증한다. `/health`(liveness)는 이 수정 대상이 아니다 — 상수 응답 유지가 의도된 설계다
 * (DB 장애 시 전체 파드 동시 재시작으로 장애를 증폭시키지 않기 위함).
 */
private fun remoteAddr(ip: String): RequestPostProcessor =
    RequestPostProcessor { request ->
        request.remoteAddr = ip
        request
    }

/**
 * readiness 그룹에 db가 실제로 포함되어 있는지 검증한다.
 * 같은 컨텍스트 안에서 "살아있을 때 UP" → "DataSource를 직접 닫아 DOWN" 순서로 상태를 뒤집어
 * 반증한다. 두 테스트를 별도 메서드로 쪼개면 컨텍스트/DataSource가 클래스 단위로 공유되어
 * 실행 순서에 따라 깨지므로(먼저 닫히면 "살아있을 때" 케이스가 실패) 한 메서드에서 순차 검증한다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    // 다른 풀 컨텍스트 테스트(ApplicationContextTest 등)와 캐시 키를 분리해, 이 테스트가 DataSource를
    // 닫아버려도 다른 테스트의 공유 컨텍스트를 오염시키지 않게 한다.
    properties = ["spring.application.name=devquest-readiness-db-down-test"],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReadinessDbDownTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `DB가 죽으면 readiness가 DOWN(503)으로 바뀐다 - 상태 조작으로 반증`() {
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk)

        (dataSource as HikariDataSource).close()

        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isServiceUnavailable)
    }
}

/**
 * readiness 그룹이 mail 지표에 영향받지 않는지 검증한다 — 이 수정의 핵심 동기(AND 과결합 회귀).
 * mail 커넥션을 로컬 닫힌 포트로 돌려 mail health indicator를 결정적으로 DOWN 시킨다
 * (외부 네트워크 의존 없이, 즉시 connection refused).
 *
 * 판별 범위(정직하게 적어둔다, QA 지적 F-1): 이 테스트는 "`include`에 mail이 다시 끼워 넣어지는
 * 회귀"는 정확히 잡는다. 그러나 `management.endpoint.health.group.readiness` 설정 블록이
 * **통째로 삭제**되는 회귀는 못 잡는다 — 이 환경에서 Boot 4가 `readinessState`만 보는 기본
 * readiness 그룹을 자동 등록하는데, `readinessState`도 mail을 보지 않으므로 설정이 없어도
 * 이 테스트는 그대로 통과한다(실측 확인됨). 그 케이스는 `ReadinessDbDownTest`(설정이 없으면
 * db가 readiness에 없어 DB를 죽여도 DOWN으로 안 바뀜)와 아래 [ReadinessGroupMembershipTest]
 * (그룹 멤버십을 직접 단언)가 대신 잡는다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=1",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReadinessMailDownTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `mail 지표가 DOWN이어도 readiness는 영향받지 않는다`() {
        // 전체 health(AND 조합)는 mail이 끌어내려 DOWN이어야 한다 — 이 테스트가 실제로 mail을
        // 죽였다는 증거(반증 대상이 정말 DOWN인지 확인 없이 readiness만 보면 조작이 안 먹었을 수 있다).
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isServiceUnavailable)

        // readiness 그룹은 db,ping만 보므로 mail이 죽어도 UP을 유지해야 한다.
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk)
    }
}

/**
 * SecurityConfig의 IP 제한 함정 회귀 가드.
 * kubelet은 파드 IP(127.0.0.1 아님)로 readiness를 찌르므로, readiness 경로만 임의 IP에서
 * 열려 있어야 한다 — 동시에 actuator 하위 나머지 IP 제한(예: prometheus)은 유지돼야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorReadinessSecurityMatcherTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `actuator health readiness는 임의 IP에서 인증 없이 접근 가능하다`() {
        mockMvc.perform(get("/actuator/health/readiness").with(remoteAddr("203.0.113.5")))
            .andExpect(status().isOk)
    }

    @Test
    fun `actuator prometheus는 여전히 IP 제한이 걸려 임의 IP에서 거부된다 - 회귀 가드`() {
        mockMvc.perform(get("/actuator/prometheus").with(remoteAddr("203.0.113.5")))
            .andExpect(status().isForbidden)
    }
}

/**
 * QA 지적 F-1 — `ReadinessMailDownTest`는 상태 조작(200/503) 관찰만으로는 "readiness 그룹의
 * include 설정이 실제로 db,ping으로 존재한다"를 증명하지 못한다(설정 블록을 통째로 지워도 그대로
 * 통과함이 실측 확인됨). 그래서 여기서는 상태 응답이 아니라 `HealthEndpointGroups` 빈을 직접
 * 조회해 readiness 그룹의 멤버십을 단언한다 — application.yml의
 * `management.endpoint.health.group.readiness.include` 설정이 사라지거나 다른 값으로 바뀌면
 * 이 테스트가 실패해야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ReadinessGroupMembershipTest {

    @Autowired
    private lateinit var healthEndpointGroups: HealthEndpointGroups

    @Test
    fun `readiness 그룹은 db와 ping만 멤버로 포함한다 - include 설정 자체를 직접 단언`() {
        val readinessGroup = requireNotNull(healthEndpointGroups.get("readiness")) {
            "readiness 그룹이 존재하지 않는다 - management.endpoint.health.group.readiness 설정 확인 필요"
        }

        assertThat(readinessGroup.isMember("db")).isTrue
        assertThat(readinessGroup.isMember("ping")).isTrue
        assertThat(readinessGroup.isMember("mail")).isFalse
    }
}
