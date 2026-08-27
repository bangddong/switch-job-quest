package com.devquest.daily.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource

/**
 * 함정 ① 회귀 가드 — daily-api의 `application-prod.yml`이 `spring.jpa.hibernate.ddl-auto`를
 * 명시적으로 `validate`로 고정하고 있는지 단정한다.
 *
 * `@SpringBootTest(webEnvironment=...) + @ActiveProfiles("prod")`로 전체 컨텍스트를 띄우는 방식은
 * 쓰지 않는다 — prod 프로필은 `${DB_HOST}` 등 실제 Postgres 접속 정보를 요구해 테스트 환경에서
 * 그대로 기동시킬 수 없다. 대신 `application-prod.yml`을 텍스트가 아니라
 * [YamlPropertySourceLoader]로 실제 로드해(Spring이 해석하는 것과 동일한 파서) 값이 조용히
 * 삭제되거나 오타로 깨지는 회귀를 잡는다. 이 값이 사라지거나 `create-drop`/`update` 등으로
 * 바뀌면 prod 기동 시 전체 스키마가 drop-create될 수 있다(daily-api KDoc·db-core.yml 참고).
 */
class DailyApiProdDdlAutoTest {

    @Test
    fun `prod 프로필의 ddl-auto는 validate로 고정되어 있다`() {
        val loader = YamlPropertySourceLoader()
        val resource = ClassPathResource("application-prod.yml")
        val sources = loader.load("application-prod", resource)

        val ddlAuto = sources.firstNotNullOfOrNull { it.getProperty("spring.jpa.hibernate.ddl-auto") }

        assertThat(ddlAuto).isEqualTo("validate")
    }

    @Test
    fun `prod 프로필은 PostgreSQLDialect를 명시한다`() {
        val loader = YamlPropertySourceLoader()
        val resource = ClassPathResource("application-prod.yml")
        val sources = loader.load("application-prod", resource)

        val dialect = sources.firstNotNullOfOrNull { it.getProperty("spring.jpa.properties.hibernate.dialect") }

        assertThat(dialect).isEqualTo("org.hibernate.dialect.PostgreSQLDialect")
    }
}
