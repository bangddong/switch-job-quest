package com.devquest.core.api.config

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import javax.sql.DataSource

/**
 * B7-21 ⓐ - `storage.datasource.core.connection-timeout` / `validation-timeout` 키가 **실제 DataSource 빈**의 HikariCP 설정까지
 * 도달하는지 본다(`CoreDataSourceConfig` 의 `@ConfigurationProperties(prefix = "storage.datasource.core")`).
 * 키 이름이 틀리면 relaxed binding 은 오류 없이 무시하고 Hikari 기본값(30초/5초)이 남는다 - 설정 파일 값만 단언하면 이를 못 잡는다.
 *
 * 기본 값(30000/5000)과 겹치지 않는 값을 프로퍼티로 주입해서, 도달 여부가 "기본값이 우연히 같아서" 통과하지 못하게 한다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "storage.datasource.core.connection-timeout=4321",
        "storage.datasource.core.validation-timeout=1234",
    ],
)
@ActiveProfiles("test")
class CoreHikariBindingTest {

    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `connection-timeout 과 validation-timeout 키는 실제 Hikari 풀 설정에 바인딩된다`() {
        val hikari = dataSource.unwrap(HikariDataSource::class.java)

        assertThat(hikari.connectionTimeout).isEqualTo(4321L)
        assertThat(hikari.validationTimeout).isEqualTo(1234L)
    }
}
