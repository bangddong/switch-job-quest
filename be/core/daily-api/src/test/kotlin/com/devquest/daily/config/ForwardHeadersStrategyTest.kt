package com.devquest.daily.config

import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * 📌D-016 B7-19-2 ⓐ — core-api `ForwardHeadersStrategyTest` 와 같은 계약을 daily-api 에도 고정한다
 * (D-007: daily-api 는 core-api 를 의존하지 않고 설정·테스트를 복제한다).
 *
 * `server.forward-headers-strategy=native` 를 `application.yml` 에 명시했는지 **실서버(Tomcat RemoteIpValve)** 로 본다.
 * 프로퍼티를 따로 주지 않는다 — 테스트(로컬·GitHub Actions)는 Kubernetes 가 감지되지 않으므로 Fly 와 같은 조건이다.
 * ⚠️ `spring.main.cloud-platform=none` 으로 감지를 끄면 안 된다: Boot 4.0.3 에서 `CloudPlatform.NONE` 도
 * `isUsingForwardHeaders()` 가 true 라(실측) 오히려 NATIVE 가 켜져, 미설정 상태에서도 통과하는 무의미한 테스트가 된다.
 * 관측용 필터(`/__forward-probe`)는 이 테스트 안에만 존재한다.
 *
 * 요청은 127.0.0.1 로 직접 보낸다 — 테스트 클라이언트가 곧 신뢰 프록시(루프백)다.
 * `framework` 로 바꾸면 "127.0.0.1 위조" 케이스가 깨진다(ForwardedHeaderFilter 는 XFF 맨 왼쪽을 그대로 쓴다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForwardHeadersStrategyTest {

    @TestConfiguration
    class ProbeConfig {
        @Bean
        fun forwardProbeFilter(): Filter = Filter { req, res, chain ->
            val request = req as HttpServletRequest
            if (request.requestURI == PROBE_PATH) {
                val response = res as HttpServletResponse
                response.contentType = "text/plain"
                response.writer.write("secure=${request.isSecure};scheme=${request.scheme};remoteAddr=${request.remoteAddr}")
            } else {
                chain.doFilter(req, res)
            }
        }
    }

    @LocalServerPort
    private var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    private fun probe(vararg headers: Pair<String, String>): String {
        val builder = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:$port$PROBE_PATH")).GET()
        headers.forEach { (name, value) -> builder.header(name, value) }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString()).body()
    }

    @Test
    fun `헤더가 없으면 직접 접속으로 본다 - 기준선`() {
        assertThat(probe()).isEqualTo("secure=false;scheme=http;remoteAddr=127.0.0.1")
    }

    @Test
    fun `X-Forwarded-Proto https 를 신뢰 프록시가 보내면 secure 로 인식된다`() {
        assertThat(probe("X-Forwarded-Proto" to "https")).contains("secure=true", "scheme=https")
    }

    @Test
    fun `신뢰 프록시가 전달한 외부 공인 IP 가 remoteAddr 가 된다`() {
        assertThat(probe("X-Forwarded-For" to "203.0.113.7")).contains("remoteAddr=203.0.113.7")
    }

    @Test
    fun `클라이언트가 XFF 맨 왼쪽에 127_0_0_1 을 위조해도 실제 외부 IP 가 remoteAddr 가 된다`() {
        // framework(ForwardedHeaderFilter)는 맨 왼쪽 127.0.0.1 을 쓰므로 여기서 깨진다.
        assertThat(probe("X-Forwarded-For" to "127.0.0.1, 203.0.113.7")).contains("remoteAddr=203.0.113.7")
    }

    private companion object {
        const val PROBE_PATH = "/__forward-probe"
    }
}
