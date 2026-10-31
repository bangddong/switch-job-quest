package com.devquest.core.api.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * 📌D-016 B7-19-2 ⓐ — `server.forward-headers-strategy` 를 `native` 로 명시 고정한 것을 **실서버로** 고정한다.
 *
 * 미설정이면 Spring Boot 는 Kubernetes 가 감지될 때만 암묵 NATIVE 로 바꾸고, Fly 는 NONE 이었다. 즉 같은 코드가
 * 플랫폼에 따라 `isSecure`·`remoteAddr` 를 다르게 봤다. 테스트(로컬·GitHub Actions)는 Kubernetes 가 감지되지 않아
 * **Fly 와 같은 조건**이므로, 명시 설정만으로 NATIVE 동작이 나오는지 본다.
 * ⚠️ `spring.main.cloud-platform=none` 으로 감지를 끄면 안 된다: Boot 4.0.3 에서 `CloudPlatform.NONE` 도
 * `isUsingForwardHeaders()` 가 true 라(실측) 오히려 NATIVE 가 켜져, 미설정 상태에서도 통과하는 무의미한 테스트가 된다.
 *
 * NATIVE(Tomcat RemoteIpValve)는 MockMvc 에서 동작하지 않으므로 `RANDOM_PORT` 로 실제 Tomcat 을 띄우고,
 * 요청은 **127.0.0.1 로 직접** 보낸다 — 테스트 클라이언트가 곧 "신뢰 프록시(루프백)"다.
 *
 * `framework` 로 바꾸면 깨진다: ForwardedHeaderFilter 는 신뢰 프록시 판정 없이 XFF 맨 왼쪽을 remoteAddr 로
 * 쓰므로, 아래 "127.0.0.1 위조" 케이스가 `/actuator/prometheus` 를 통과해 버린다(기각 근거).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        // 다른 풀 컨텍스트 테스트와 캐시 키를 분리한다.
        "spring.application.name=devquest-forward-headers-test",
    ],
)
@ActiveProfiles("test")
class ForwardHeadersStrategyTest {

    @LocalServerPort
    private var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    private fun get(path: String, vararg headers: Pair<String, String>): HttpResponse<String> {
        val builder = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:$port$path")).GET()
        headers.forEach { (name, value) -> builder.header(name, value) }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `X-Forwarded-Proto https 를 신뢰 프록시가 보내면 요청이 secure 로 인식된다 - HSTS 헤더로 관측`() {
        // HSTS 는 Spring Security 가 request.isSecure 일 때만 붙인다.
        val forwarded = get("/health", "X-Forwarded-Proto" to "https")
        val direct = get("/health")

        assertThat(forwarded.statusCode()).isEqualTo(200)
        assertThat(forwarded.headers().firstValue("Strict-Transport-Security")).isPresent
        assertThat(direct.headers().firstValue("Strict-Transport-Security")).isEmpty
    }

    @Test
    fun `루프백 직접 접근은 actuator prometheus 가 열려 있다 - 기준선`() {
        // 아래 403 단언들이 "엔드포인트가 원래 닫혀 있어서"가 아님을 증명하는 대조군.
        assertThat(get("/actuator/prometheus").statusCode()).isEqualTo(200)
    }

    @Test
    fun `신뢰 프록시가 전달한 외부 공인 IP 는 remoteAddr 가 되어 actuator 가 403 이다`() {
        val response = get("/actuator/prometheus", "X-Forwarded-For" to "203.0.113.7")

        assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `클라이언트가 XFF 맨 왼쪽에 127_0_0_1 을 위조해도 실제 외부 IP 가 remoteAddr 이라 actuator 가 403 이다`() {
        // 체인: 위조값(127.0.0.1) → 실제 클라이언트(203.0.113.7) → (루프백 = 신뢰 프록시).
        // RemoteIpValve 는 오른쪽부터 신뢰 프록시를 벗겨내다 203.0.113.7 에서 멈춘다.
        // framework(ForwardedHeaderFilter)는 맨 왼쪽 127.0.0.1 을 쓰므로 여기서 200 이 되어 이 테스트가 깨진다.
        val response = get("/actuator/prometheus", "X-Forwarded-For" to "127.0.0.1, 203.0.113.7")

        assertThat(response.statusCode()).isEqualTo(403)
    }
}
