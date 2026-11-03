package com.devquest.core.api.measurement

import org.junit.jupiter.api.Tag
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * M1·M2 를 실제 Postgres(Testcontainers)로 반복한다 — H2 결과가 DB 종류 때문에 달라지는지 확인하는 용도.
 * Docker 가 필요해서 `measurement` 태그로 기본 `test` 에서 뺐다: `./gradlew :core:core-api:measurementTest`.
 */
@Tag("measurement")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CodingOsivPostgresDefaultMeasurementTest : AbstractCodingOsivMeasurement(
    config = "A:osiv-default(Postgres)",
    osivInterceptorBeans = 1,
    activeWhileHeld = 1,
) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) = PostgresMeasurementSupport.register(registry)
    }
}

@Tag("measurement")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = ["spring.jpa.open-in-view=false"],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CodingOsivPostgresDisabledMeasurementTest : AbstractCodingOsivMeasurement(
    config = "B:osiv-false(Postgres)",
    osivInterceptorBeans = 0,
    activeWhileHeld = 0,
) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) = PostgresMeasurementSupport.register(registry)
    }
}
