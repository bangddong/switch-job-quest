package com.devquest.core.api.config

import com.devquest.client.ai.evaluator.TechBlogEvaluator
import com.devquest.core.api.adapter.ai.http.BlogHttpEvaluator
import com.devquest.core.domain.port.BlogEvaluatorPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * 서비스 분해 Phase 0 Task 0.4 — `devquest.ai.transport` 전환 스위치 슬라이스 테스트.
 *
 * 소비 서비스(AiCheckService 등)는 포트 인터페이스만 주입받으므로, 실제 주입되는 구현체가
 * transport 값에 따라 바뀌는지를 대표 포트(BlogEvaluatorPort)로 증명한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AiTransportInprocessSwitchTest {

    @Autowired
    private lateinit var blogEvaluatorPort: BlogEvaluatorPort

    @Test
    fun `devquest ai transport 미설정 시 client-ai 구현이 주입된다`() {
        assertThat(blogEvaluatorPort).isInstanceOf(TechBlogEvaluator::class.java)
    }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["devquest.ai.transport=http"],
)
@ActiveProfiles("test")
class AiTransportHttpSwitchTest {

    @Autowired
    private lateinit var blogEvaluatorPort: BlogEvaluatorPort

    @Test
    fun `devquest ai transport=http 시 HTTP 어댑터가 주입된다`() {
        assertThat(blogEvaluatorPort).isInstanceOf(BlogHttpEvaluator::class.java)
    }
}
