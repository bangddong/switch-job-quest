package com.devquest.ai.adapter

import com.devquest.core.domain.model.AiCallLog
import com.devquest.core.domain.port.AiCallLogPort
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * ai-api용 AiCallLogPort 관측 어댑터.
 *
 * 서비스 분해 에픽 Phase 1 Task 1.3 — `client-ai`의 `CacheMetricsAdvisor`가 매 AI 호출마다
 * 요구하는 `AiCallLogPort`를 core DB(`db-core`의 `AiCallLogAdapter`) 없이 충족시킨다.
 * 구조화 로그 1줄 + Micrometer 메트릭만 기록하고 DB 쓰기는 하지 않는다
 * (Task 0.2 방침 A 확정 — `AiCallLog` 읽기 소비처 0건 조사 완료, 잃는 것 없음).
 *
 * ⚠️ 메트릭 네임스페이스를 `client-ai`의 `CacheMetricsAdvisor`가 이미 기록하는
 * `ai.call.duration`·`ai.tokens.input`·`ai.tokens.output`·`ai.tokens.cache_read`·
 * `ai.tokens.cache_creation`과 겹치지 않게 `ai.call.log.*`로 분리한다. Task 1.1에서
 * `client-ai`가 ai-api 프로세스에 붙으면 `CacheMetricsAdvisor`와 이 어댑터가 같은
 * 호출 1건에 대해 동시에 도는 구조라, 이름이 같으면 값이 이중 계상된다.
 */
@Component
class AiCallLogObservabilityAdapter(
    private val meterRegistry: MeterRegistry,
) : AiCallLogPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun record(log: AiCallLog) {
        this.log.info(
            "ai_call_log_recorded evaluator={} model={} inputTokens={} outputTokens={} " +
                "cacheReadTokens={} cacheCreationTokens={} latencyMs={} success={}",
            log.evaluatorName,
            log.modelName,
            log.inputTokens,
            log.outputTokens,
            log.cacheReadTokens,
            log.cacheCreationTokens,
            log.latencyMs,
            log.success,
        )

        meterRegistry.counter(
            "ai.call.log.recorded",
            "evaluator", log.evaluatorName,
            "model", log.modelName,
            "success", log.success.toString(),
        ).increment()

        meterRegistry.timer(
            "ai.call.log.latency",
            "evaluator", log.evaluatorName,
            "model", log.modelName,
        ).record(log.latencyMs, TimeUnit.MILLISECONDS)
    }
}
