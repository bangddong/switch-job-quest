package com.devquest.core.api.measurement

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles

/**
 * 구성 A — 현재 설정. `spring.jpa.open-in-view` 가 be 어디에도 없어 Boot 기본값(true)이다.
 * 기대: 외부 경계 블록 중 요청 스레드가 이미 JPA 로 커넥션을 쥐고 있다 (active=1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CodingOsivDefaultMeasurementTest : AbstractCodingOsivMeasurement(
    config = "A:osiv-default(H2)",
    osivInterceptorBeans = 1,
    activeWhileHeld = 1,
)

/**
 * 구성 B — `spring.jpa.open-in-view=false` (테스트 프로퍼티로만 주입, 설정 파일은 건드리지 않는다).
 * 기대: 어댑터 호출마다 커넥션을 빌렸다 즉시 돌려주므로 블록 중 active=0, 요청은 그대로 성공한다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = ["spring.jpa.open-in-view=false"],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CodingOsivDisabledMeasurementTest : AbstractCodingOsivMeasurement(
    config = "B:osiv-false(H2)",
    osivInterceptorBeans = 0,
    activeWhileHeld = 0,
)
