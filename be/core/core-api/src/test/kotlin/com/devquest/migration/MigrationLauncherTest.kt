package com.devquest.migration

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.flywaydb.core.api.output.RepairResult
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * 마이그레이션 모드의 인자 분기와 종료 코드 단위 테스트(DB 없음).
 * 실제 DB·컨텍스트 동작은 [MigrationLauncherIntegrationTest]가 진짜 Postgres로 검증한다.
 */
class MigrationLauncherTest {

    @Test
    fun `첫 인자 migrate validate repair 는 마이그레이션 모드로 인식된다`() {
        assertThat(MigrationMode.parse(arrayOf("migrate"))).isEqualTo(MigrationMode.MIGRATE)
        assertThat(MigrationMode.parse(arrayOf("validate", "--x=y"))).isEqualTo(MigrationMode.VALIDATE)
        assertThat(MigrationMode.parse(arrayOf("repair"))).isEqualTo(MigrationMode.REPAIR)
    }

    @Test
    fun `인자가 없거나 옵션으로 시작하면 일반 기동이다`() {
        assertThat(MigrationMode.parse(emptyArray())).isNull()
        assertThat(MigrationMode.parse(arrayOf("--spring.profiles.active=prod"))).isNull()
    }

    @Test
    fun `알 수 없는 맨 단어 인자는 조용히 웹 서버를 띄우지 않고 예외로 거부한다`() {
        // release_command 오타("migrat")가 웹 서버를 띄워 5분 타임아웃까지 매달리는 것을 막는다.
        assertThatThrownBy { MigrationMode.parse(arrayOf("migrat")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("migrat")
    }

    @Test
    fun `migrate 는 repair 를 호출하지 않고 migrate 만 호출하며 성공이면 0`() {
        val flyway: Flyway = mock(defaultAnswer = RETURNS_DEEP_STUBS)

        val code = MigrationExecutor.execute(MigrationMode.MIGRATE, flyway)

        assertThat(code).isEqualTo(0)
        verify(flyway).migrate()
        verify(flyway, never()).repair()
    }

    @Test
    fun `migrate 가 예외를 던지면 non-zero 를 반환하고 repair 로 덮지 않는다`() {
        val flyway: Flyway = mock(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(flyway.migrate()).thenThrow(FlywayException("Validate failed: checksum mismatch"))

        val code = MigrationExecutor.execute(MigrationMode.MIGRATE, flyway)

        assertThat(code).isNotEqualTo(0)
        verify(flyway, never()).repair()
    }

    @Test
    fun `validate 는 validate 만 호출한다`() {
        val flyway: Flyway = mock(defaultAnswer = RETURNS_DEEP_STUBS)

        val code = MigrationExecutor.execute(MigrationMode.VALIDATE, flyway)

        assertThat(code).isEqualTo(0)
        verify(flyway).validate()
        verify(flyway, never()).migrate()
        verify(flyway, never()).repair()
    }

    @Test
    fun `validate 실패는 non-zero`() {
        val flyway: Flyway = mock(defaultAnswer = RETURNS_DEEP_STUBS)
        whenever(flyway.validate()).thenThrow(FlywayException("Validate failed"))

        assertThat(MigrationExecutor.execute(MigrationMode.VALIDATE, flyway)).isNotEqualTo(0)
    }

    @Test
    fun `repair 는 repair 만 호출한다`() {
        val flyway: Flyway = mock(defaultAnswer = RETURNS_DEEP_STUBS)
        // RepairResult 는 public 필드라 mock 에서는 null 이다 — 실제 객체를 쓴다.
        whenever(flyway.repair()).thenReturn(RepairResult("test", "test"))

        val code = MigrationExecutor.execute(MigrationMode.REPAIR, flyway)

        assertThat(code).isEqualTo(0)
        inOrder(flyway) { verify(flyway).repair() }
        verify(flyway, never()).migrate()
    }
}
