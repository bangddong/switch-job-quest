package com.devquest.ai.adapter

import com.devquest.core.domain.model.AiCallLog
import com.devquest.core.domain.port.AiCallLogPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import javax.sql.DataSource

/**
 * 서비스 분해 에픽 Phase 1 Task 1.3 — ai-api 컨텍스트에서 AiCallLogPort가
 * AiCallLogObservabilityAdapter(관측 어댑터)로 주입되고, core DB(DataSource)에
 * 의존하지 않는지 검증한다.
 */
@SpringBootTest
class AiCallLogPortWiringTest {

    @Autowired
    private lateinit var aiCallLogPort: AiCallLogPort

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `AiCallLogPort 빈은 AiCallLogObservabilityAdapter로 주입된다`() {
        assertThat(aiCallLogPort).isInstanceOf(AiCallLogObservabilityAdapter::class.java)
    }

    @Test
    fun `record 호출은 예외를 던지지 않는다`() {
        aiCallLogPort.record(
            AiCallLog(
                evaluatorName = "TestEvaluator",
                modelName = "claude-test",
                inputTokens = 100,
                outputTokens = 50,
                cacheReadTokens = 10,
                cacheCreationTokens = 0,
                latencyMs = 123L,
                success = true,
            )
        )
    }

    @Test
    fun `ai-api 컨텍스트에는 DataSource 빈이 없다 (core DB 미의존 격리 확인)`() {
        val dataSourceBeans = applicationContext.getBeanNamesForType(DataSource::class.java)

        assertThat(dataSourceBeans).isEmpty()
    }
}
