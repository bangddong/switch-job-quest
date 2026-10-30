package com.devquest.core.api.support

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.databind.ObjectMapper
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 기술 면접 한도 키는 IP가 아니라 **인증된 사용자 ID**다 (📌D-016 B7-19 1단계).
 * IP는 조작 가능하지만 JWT가 검증한 principal(`github-<id>`)은 조작할 수 없다.
 */
class TechInterviewRateLimitInterceptorTest {

    private lateinit var store: RateLimitBucketStore
    private lateinit var interceptor: TechInterviewRateLimitInterceptor

    @BeforeEach
    fun setUp() {
        store = RateLimitBucketStore(capacity = 2, refillDays = 1)
        interceptor = TechInterviewRateLimitInterceptor(store, ObjectMapper(), memoryOnlyLimiter())
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun loginAs(userId: String) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            userId, null, listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
    }

    private fun mockRequest(ip: String = "1.2.3.4", flyClientIp: String? = null): HttpServletRequest {
        val request = mock<HttpServletRequest>()
        whenever(request.getHeader("Fly-Client-IP")).thenReturn(flyClientIp)
        whenever(request.getHeader("X-Forwarded-For")).thenReturn(null)
        whenever(request.remoteAddr).thenReturn(ip)
        return request
    }

    private fun mockResponse(): HttpServletResponse {
        val response = mock<HttpServletResponse>()
        whenever(response.writer).thenReturn(PrintWriter(StringWriter()))
        return response
    }

    @Test
    fun `로그인 사용자의 첫 번째 요청은 통과한다`() {
        loginAs("github-1")
        assertThat(interceptor.preHandle(mockRequest(), mockResponse(), Any())).isTrue()
    }

    @Test
    fun `같은 사용자의 두 번째 요청(평가)도 통과한다`() {
        loginAs("github-1")
        interceptor.preHandle(mockRequest(), mockResponse(), Any())
        assertThat(interceptor.preHandle(mockRequest(), mockResponse(), Any())).isTrue()
    }

    @Test
    fun `같은 사용자의 세 번째 요청은 429로 차단된다`() {
        loginAs("github-1")
        interceptor.preHandle(mockRequest(), mockResponse(), Any())
        interceptor.preHandle(mockRequest(), mockResponse(), Any())
        val response = mockResponse()

        val result = interceptor.preHandle(mockRequest(), response, Any())

        assertThat(result).isFalse()
        verify(response).setStatus(429)
    }

    @Test
    fun `다른 사용자는 독립적으로 카운트된다`() {
        loginAs("github-1")
        repeat(2) { interceptor.preHandle(mockRequest(), mockResponse(), Any()) }

        loginAs("github-2")
        assertThat(interceptor.preHandle(mockRequest(), mockResponse(), Any())).isTrue()
    }

    @Test
    fun `IP가 달라도 같은 사용자는 같은 버킷을 쓴다 - IP 바꿔치기로 한도를 못 늘린다`() {
        loginAs("github-1")
        interceptor.preHandle(mockRequest(ip = "10.0.0.1", flyClientIp = "1.1.1.1"), mockResponse(), Any())
        interceptor.preHandle(mockRequest(ip = "10.0.0.2", flyClientIp = "2.2.2.2"), mockResponse(), Any())

        val result = interceptor.preHandle(mockRequest(ip = "10.0.0.3", flyClientIp = "3.3.3.3"), mockResponse(), Any())

        assertThat(result).isFalse()
    }

    @Test
    fun `같은 IP여도 사용자가 다르면 서로 한도에 영향을 주지 않는다`() {
        loginAs("github-1")
        repeat(2) { interceptor.preHandle(mockRequest(ip = "1.2.3.4"), mockResponse(), Any()) }

        loginAs("github-2")
        assertThat(interceptor.preHandle(mockRequest(ip = "1.2.3.4"), mockResponse(), Any())).isTrue()
    }

    @Test
    fun `인증 정보가 없으면 403으로 거부하고 버킷을 만들지 않는다`() {
        val response = mockResponse()

        val result = interceptor.preHandle(mockRequest(), response, Any())

        assertThat(result).isFalse()
        verify(response).setStatus(403)
        assertThat(store.size()).isZero()
    }

    @Test
    fun `AnonymousAuthenticationToken이면 403으로 거부한다`() {
        SecurityContextHolder.getContext().authentication = AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
        )
        val response = mockResponse()

        val result = interceptor.preHandle(mockRequest(), response, Any())

        assertThat(result).isFalse()
        verify(response).setStatus(403)
        assertThat(store.size()).isZero()
    }

    @Test
    fun `principal이 문자열 anonymousUser여도 사용자로 취급하지 않는다`() {
        // permitAll 경로의 익명 요청은 principal이 문자열 "anonymousUser"로 들어온다 (U-3).
        // `principal as? String`만으로 판정하면 이 값이 하나의 "사용자"로 버킷을 공유하게 된다.
        loginAs("anonymousUser")
        val response = mockResponse()

        val result = interceptor.preHandle(mockRequest(), response, Any())

        assertThat(result).isFalse()
        verify(response).setStatus(403)
        assertThat(store.size()).isZero()
    }

    @Test
    fun `principal이 문자열이 아니면 403으로 거부한다`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            Any(), null, listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        val response = mockResponse()

        assertThat(interceptor.preHandle(mockRequest(), response, Any())).isFalse()
        verify(response).setStatus(403)
    }

    @Test
    fun `store clear 후에는 다시 2회 허용된다`() {
        loginAs("github-1")
        repeat(2) { interceptor.preHandle(mockRequest(), mockResponse(), Any()) }
        store.clear()
        assertThat(interceptor.preHandle(mockRequest(), mockResponse(), Any())).isTrue()
    }
}
