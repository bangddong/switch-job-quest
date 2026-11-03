package com.devquest.core.api.osiv

import com.devquest.core.api.measurement.PostgresMeasurementSupport
import org.junit.jupiter.api.Tag
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * [CodingExternalWaitConnectionTest] 를 실제 Postgres(Testcontainers)로 반복한다 - DB 종류가 결과를 바꾸지 않음을 확인하는 용도.
 * 설정 오버라이드는 DB 접속 정보뿐이고 `spring.jpa.open-in-view` 는 건드리지 않는다.
 * Docker 가 필요해서 `measurement` 태그로 기본 `test` 에서 뺐다: `./gradlew :core:core-api:measurementTest`.
 */
@Tag("measurement")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CodingExternalWaitPostgresConnectionTest : AbstractCodingExternalWaitConnectionTest(config = "app-default(Postgres)") {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun datasource(registry: DynamicPropertyRegistry) = PostgresMeasurementSupport.register(registry)
    }
}
