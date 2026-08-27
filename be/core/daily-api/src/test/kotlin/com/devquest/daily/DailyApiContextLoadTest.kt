package com.devquest.daily

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * daily-api가 독립적으로 Spring 컨텍스트를 로드할 수 있는지 검증한다.
 *
 * 함정 ②(`@EntityScan` 누락)가 재발하면 이 테스트가 `Not a managed type` 예외와 함께 여기서
 * 즉시 죽는다 — 레포지토리 빈은 등록되지만 관리 대상 엔티티가 0개이기 때문.
 */
@SpringBootTest
class DailyApiContextLoadTest {

    @Test
    fun `daily-api Spring 컨텍스트가 정상적으로 로드된다`() {
        // 컨텍스트 로드 중 예외가 없으면 성공 — 별도 assertion 불필요
    }
}
