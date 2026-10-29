package com.devquest.daily.support

import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ClientIpResolverTest {

    private val secret = "test-edge-secret"

    private fun request(
        edgeAuth: String? = null,
        edgeClientIp: String? = null,
        flyClientIp: String? = null,
        xForwardedFor: String? = null,
        remoteAddr: String = "9.9.9.9",
    ): HttpServletRequest {
        val request = mock<HttpServletRequest>()
        whenever(request.getHeader("X-DevQuest-Edge-Auth")).thenReturn(edgeAuth)
        whenever(request.getHeader("X-DevQuest-Client-IP")).thenReturn(edgeClientIp)
        whenever(request.getHeader("Fly-Client-IP")).thenReturn(flyClientIp)
        whenever(request.getHeader("X-Forwarded-For")).thenReturn(xForwardedFor)
        whenever(request.remoteAddr).thenReturn(remoteAddr)
        return request
    }

    @Test
    fun `비밀이 일치하면 Edge Client-IP 헤더 값을 IP 로 쓴다`() {
        val resolver = ClientIpResolver(secret)
        val ip = resolver.resolve(request(edgeAuth = secret, edgeClientIp = "203.0.113.7", flyClientIp = "172.68.0.1"))
        assertThat(ip).isEqualTo("203.0.113.7")
    }

    @Test
    fun `Edge Client-IP 헤더 앞뒤 공백은 제거한다`() {
        val resolver = ClientIpResolver(secret)
        val ip = resolver.resolve(request(edgeAuth = secret, edgeClientIp = "  203.0.113.7 "))
        assertThat(ip).isEqualTo("203.0.113.7")
    }

    @Test
    fun `비밀이 다르면 헤더 IP 를 무시하고 기존 판정으로 폴백한다`() {
        val resolver = ClientIpResolver(secret)
        val ip = resolver.resolve(request(edgeAuth = "wrong-secret", edgeClientIp = "203.0.113.7", flyClientIp = "172.68.0.1"))
        assertThat(ip).isEqualTo("172.68.0.1")
    }

    @Test
    fun `비밀이 미설정이면 헤더가 있어도 무시하고 기존 판정으로 폴백한다`() {
        val resolver = ClientIpResolver("")
        val ip = resolver.resolve(request(edgeAuth = "", edgeClientIp = "203.0.113.7", flyClientIp = "172.68.0.1"))
        assertThat(ip).isEqualTo("172.68.0.1")
    }

    @Test
    fun `비밀이 공백뿐이어도 미설정으로 본다`() {
        val resolver = ClientIpResolver("   ")
        val ip = resolver.resolve(request(edgeAuth = "   ", edgeClientIp = "203.0.113.7", flyClientIp = "172.68.0.1"))
        assertThat(ip).isEqualTo("172.68.0.1")
    }

    @Test
    fun `Edge Client-IP 가 공백이면 기존 판정으로 폴백한다`() {
        val resolver = ClientIpResolver(secret)
        val ip = resolver.resolve(request(edgeAuth = secret, edgeClientIp = "   ", flyClientIp = "172.68.0.1"))
        assertThat(ip).isEqualTo("172.68.0.1")
    }

    @Test
    fun `Edge-Auth 헤더가 없으면 기존 판정으로 폴백한다`() {
        val resolver = ClientIpResolver(secret)
        val ip = resolver.resolve(request(edgeClientIp = "203.0.113.7", flyClientIp = "172.68.0.1"))
        assertThat(ip).isEqualTo("172.68.0.1")
    }

    @Test
    fun `기존 판정 우선순위는 Fly-Client-IP 다음 XFF 첫 값 다음 remoteAddr 이다`() {
        val resolver = ClientIpResolver(secret)
        assertThat(resolver.resolve(request(flyClientIp = "1.1.1.1", xForwardedFor = "2.2.2.2, 3.3.3.3")))
            .isEqualTo("1.1.1.1")
        assertThat(resolver.resolve(request(xForwardedFor = "2.2.2.2, 3.3.3.3")))
            .isEqualTo("2.2.2.2")
        assertThat(resolver.resolve(request(remoteAddr = "4.4.4.4")))
            .isEqualTo("4.4.4.4")
    }
}
