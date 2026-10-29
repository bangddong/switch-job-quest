package com.devquest.daily.support

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 익명 레이트 리밋이 쓰는 클라이언트 IP 판정 (core-api 의 같은 이름 클래스와 동일 규칙 — D-007 복제).
 *
 * 판정 순서
 * 1. 공유 비밀(`devquest.edge.auth-secret`)이 설정돼 있고, 요청의 `X-DevQuest-Edge-Auth` 가 그 비밀과
 *    상수시간 비교로 일치하며, `X-DevQuest-Client-IP` 가 비어 있지 않으면 → 그 값.
 *    Cloudflare Worker 가 `CF-Connecting-IP` 를 담아 보내는 경로다. Fly 의 `Fly-Client-IP` 는 Worker
 *    이그레스(Cloudflare 주소)로 채워져 같은 엣지의 사용자들이 버킷을 공유하므로 이 경로가 필요하다.
 * 2. 그 외 전부 → 기존 판정: `Fly-Client-IP` → `X-Forwarded-For` 첫 값 → `remoteAddr`.
 *
 * 🔑 불일치·미설정을 "고정 untrusted 버킷" 으로 보내지 않는 것(fail-soft)이 핵심 결정이다. 비밀을 Worker·Fly·EKS
 * 에 넣는 순서는 원자적이지 않고(교체 중 수 초~수십 초 불일치) Vercel 복귀 경로는 헤더 자체가 없다.
 * 고정 버킷으로 보내면 evaluate 용량 1 때문에 전원이 하루 동안 막힌다. 비용 상한은 전체 일일 상한이 지킨다.
 *
 * 원본(api/eks 호스트)은 Worker 를 안 거치고 직접 호출될 수 있어 클라이언트가 보내는 헤더는 신뢰하지 않는다 —
 * 비밀이 맞을 때만 신뢰 헤더를 읽는다. 비밀·헤더 값과 불일치 요청은 로그에 남기지 않는다(직접 호출 공격 시 로그 범람).
 */
@Component
class ClientIpResolver(
    @Value("\${devquest.edge.auth-secret:}") authSecret: String,
) : InitializingBean {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secretBytes: ByteArray? = authSecret.trim().takeIf { it.isNotEmpty() }
        ?.toByteArray(StandardCharsets.UTF_8)

    override fun afterPropertiesSet() {
        // 기동 시 한 번만. 요청마다 남기면 로그가 범람한다.
        if (secretBytes == null) {
            log.warn("devquest.edge.auth-secret 이 비어 있습니다. Worker 의 신뢰 클라이언트 IP 헤더를 쓰지 않고 기존 IP 판정(Fly-Client-IP → XFF → remoteAddr)만 사용합니다.")
        }
    }

    fun resolve(request: HttpServletRequest): String = trustedEdgeClientIp(request) ?: legacyClientIp(request)

    private fun trustedEdgeClientIp(request: HttpServletRequest): String? {
        val expected = secretBytes ?: return null
        val presented = request.getHeader(EDGE_AUTH_HEADER) ?: return null
        if (!MessageDigest.isEqual(expected, presented.toByteArray(StandardCharsets.UTF_8))) return null
        return request.getHeader(EDGE_CLIENT_IP_HEADER)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun legacyClientIp(request: HttpServletRequest): String =
        request.getHeader("Fly-Client-IP")
            ?: request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr

    companion object {
        const val EDGE_AUTH_HEADER = "X-DevQuest-Edge-Auth"
        const val EDGE_CLIENT_IP_HEADER = "X-DevQuest-Client-IP"
    }
}
