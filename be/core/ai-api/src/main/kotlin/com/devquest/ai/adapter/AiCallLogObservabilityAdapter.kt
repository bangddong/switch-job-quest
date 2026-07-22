package com.devquest.ai.adapter

import com.devquest.core.domain.model.AiCallLog
import com.devquest.core.domain.port.AiCallLogPort
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

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
 * 호출 1건에 대해 동시에 도는 구조다.
 *
 * **지연시간 메트릭은 별도로 기록하지 않는다.** 이 어댑터가 받는 `latencyMs`는
 * `CacheMetricsAdvisor`가 이미 측정해 `AiCallLog`에 실어 넘긴 값 그대로이므로, 여기서
 * 다시 타이머로 기록하면 `ai.call.duration`과 완전히 같은 숫자를 두 이름으로 중복 기록하게
 * 된다(같은 구간을 다르게 잰 것이 아니라 값 자체가 동일). `ai.call.duration`을 authoritative
 * 지연시간 지표로 두고, 이 어댑터는 advisor에 없는 축(호출 건수·성공여부)만
 * `ai.call.log.recorded` 카운터로 보강한다(#304 QA MEDIUM 이월 처리).
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
    }
}
