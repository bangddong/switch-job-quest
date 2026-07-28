package com.devquest.core.api.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * fix/timezone-consistency — 저장은 ambient zone(`LocalDateTime.now()`), 조회는 명시적
 * `Asia/Seoul`으로 되어 있어 두 값이 어긋나던 결함의 회귀 가드.
 *
 * 근본 수정은 "JVM 기본 zone을 `Asia/Seoul`로 고정"(Dockerfile ENTRYPOINT `-Duser.timezone`,
 * `build.gradle.kts` 테스트 jvmArgs)이다. 이 테스트는 그 설정이 이후 실수로 지워지거나
 * 우회되면(예: `tasks.withType<Test>` 블록 삭제) 조용히 원상복귀되는 것을 막는다.
 *
 * RED 재현 방법(로컬 macOS는 시스템 기본 zone이 이미 Asia/Seoul이라 설정 없이도 통과할 수 있음):
 *   TZ=UTC ./gradlew :core:core-api:test --tests "*.TimezoneConsistencyTest" --rerun
 * 로 강제로 다른 zone을 주입해 실패를 확인한다.
 */
class TimezoneConsistencyTest {

    @Test
    fun `JVM 기본 zone은 Asia_Seoul 이다`() {
        assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Asia/Seoul"))
    }

    @Test
    fun `zone 미지정 now()와 명시적 KST now()의 날짜가 동일하다 (자정~09시 경계 회귀 가드)`() {
        val ambient = LocalDateTime.now()
        val explicitKst = LocalDateTime.now(ZoneId.of("Asia/Seoul"))

        assertThat(ambient.toLocalDate()).isEqualTo(explicitKst.toLocalDate())
    }
}
