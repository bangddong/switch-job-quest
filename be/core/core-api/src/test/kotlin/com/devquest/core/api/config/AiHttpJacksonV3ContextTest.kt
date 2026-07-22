package com.devquest.core.api.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.ObjectMapper

/**
 * Step 0 실측 — Jackson 2/3 비대칭 해소(QA MEDIUM #1) 근거 테스트.
 *
 * `BaseAiHttpAdapter`는 지금까지 Jackson 2(`com.fasterxml.jackson.databind.ObjectMapper`)로 직접
 * (역)직렬화했지만, ai-api 서버(Boot 4 기본)는 Jackson 3(`tools.jackson.databind.json.JsonMapper`)로
 * (역)직렬화한다 — core가 J2로 쓰고 ai-api가 J3로 읽는 비대칭. 이 테스트는 교체를 진행하기 전에
 * "core-api Spring 컨텍스트에 J3 매퍼 빈이 실제로 존재하는가", "Kotlin 모듈(널러블·기본값 필드)이
 * 등록돼 있는가"를 직접 확인한다(추측 금지, 실측 우선).
 *
 * `AiCheckService`·`CompanyService`가 이미 생성자로 이 타입을 주입받아 쓰고 있어(둘 다
 * `import tools.jackson.databind.ObjectMapper`) 컨텍스트에 이 빈이 존재한다는 간접 증거는 있었지만,
 * `BaseAiHttpAdapter`가 요구하는 "필드 생략 시 Kotlin 기본값 복원", "명시적 null이 nullable 필드에
 * 매핑"까지 실측한 적은 없었다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AiHttpJacksonV3ContextTest {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    data class SampleWithDefaultsAndNullable(
        val required: String = "",
        val withDefault: Int = 42,
        val nullable: String? = null,
    )

    @Test
    fun `core-api 컨텍스트에 Jackson 3 ObjectMapper 빈이 존재한다`() {
        assertThat(objectMapper).isNotNull()
        assertThat(objectMapper.javaClass.name).startsWith("tools.jackson")
    }

    @Test
    fun `Kotlin 모듈이 등록돼 생략된 필드에 기본값이 복원된다`() {
        val result = objectMapper.readValue("""{"required":"x"}""", SampleWithDefaultsAndNullable::class.java)

        assertThat(result.required).isEqualTo("x")
        assertThat(result.withDefault).isEqualTo(42)
        assertThat(result.nullable).isNull()
    }

    @Test
    fun `명시적 null이 nullable 필드에 정상 매핑된다`() {
        val result = objectMapper.readValue(
            """{"required":"x","withDefault":1,"nullable":null}""",
            SampleWithDefaultsAndNullable::class.java,
        )

        assertThat(result.nullable).isNull()
    }

    @Test
    fun `writeValueAsString 후 readValue 라운드트립이 값을 보존한다`() {
        val original = SampleWithDefaultsAndNullable(required = "y", withDefault = 7, nullable = "n")

        val json = objectMapper.writeValueAsString(original)
        val restored = objectMapper.readValue(json, SampleWithDefaultsAndNullable::class.java)

        assertThat(restored).isEqualTo(original)
    }
}
