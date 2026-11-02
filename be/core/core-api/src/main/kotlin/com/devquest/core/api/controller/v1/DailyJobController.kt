package com.devquest.core.api.controller.v1

import com.devquest.core.domain.DailyJobService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 09:00 일일 작업의 HTTP 트리거. Cloudflare Worker Cron 이 Fly 로 직접(`api.quest.dhbang.co.kr`) 보낸다.
 *
 * - 경로가 `/api` 밖이다 — Worker 의 `isApiPath` 밖이라 Worker 가 edge 비밀을 대신 붙이는 일이 없다. edge 비밀을 재사용하면
 *   `/api` 하위로 나가는 모든 요청에 붙어 있는 값이라 누구나 트리거할 수 있게 되므로 **전용 비밀**(`DEVQUEST_JOB_SECRET`)을 쓴다.
 * - 인증은 SecurityConfig(permitAll)가 아니라 여기서 `X-DevQuest-Job-Auth` 헤더로 한다.
 * - **fail-closed**: 서버 비밀이 비어 있으면 어떤 요청도 거부한다(503). `ClientIpResolver` 의 fail-soft(비밀 없으면 기존 판정)와
 *   반대다 — 메일을 보내는 트리거는 막혀야 안전하다. EKS 처럼 비밀이 없는 환경은 503 에 머문다(의도).
 * - 응답은 ApiResponse 래퍼 없이 `{"status": ...}` 평문 계약이다(Worker 가 status 만 본다).
 */
@RestController
@RequestMapping("/internal/jobs")
class DailyJobController(
    private val dailyJobService: DailyJobService,
    @Value("\${devquest.jobs.trigger-secret:}") triggerSecret: String,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 길이가 다른 입력에서 비교가 일찍 끝나는 신호를 없애려고 SHA-256 다이제스트끼리 상수 시간 비교한다.
    private val secretDigest: ByteArray? = triggerSecret.trim().takeIf { it.isNotEmpty() }?.let(::sha256)

    init {
        if (secretDigest == null) {
            log.warn("devquest.jobs.trigger-secret 이 비어 있습니다. POST /internal/jobs/daily 는 항상 503(disabled) 을 돌려줍니다.")
        }
    }

    @PostMapping("/daily")
    fun runDaily(
        @RequestHeader("X-DevQuest-Job-Auth", required = false) providedSecret: String?,
        // body 는 관측용 힌트일 뿐이다. 깨진 body 때문에 메일이 안 나가면 안 되므로 문자열로 받아 아래에서 관대하게 해석한다.
        @RequestBody(required = false) body: String?,
    ): ResponseEntity<Map<String, Any>> {
        val expected = secretDigest
            ?: return respond(HttpStatus.SERVICE_UNAVAILABLE, "disabled")
        if (providedSecret == null || !MessageDigest.isEqual(expected, sha256(providedSecret))) {
            return respond(HttpStatus.UNAUTHORIZED, "unauthorized")
        }

        val result = dailyJobService.runDaily(parseScheduledTime(body))
            ?: return respond(HttpStatus.CONFLICT, "running")

        return ResponseEntity.ok(
            mapOf(
                "status" to "done",
                "mailTargets" to result.mailTargets,
                "mailSent" to result.mailSent,
                "expiredRateLimitRowsDeleted" to result.expiredRateLimitRowsDeleted,
            ),
        )
    }

    private fun respond(status: HttpStatus, label: String): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(status).body(mapOf("status" to label))

    private fun parseScheduledTime(body: String?): Long? {
        if (body.isNullOrBlank()) return null
        return try {
            val node = objectMapper.readTree(body).get("scheduledTime")
            if (node != null && node.isIntegralNumber) node.asLong() else null
        } catch (e: Exception) {
            log.warn("일일 작업 body 해석 실패 — scheduledTime 없이 진행합니다: {}", e.javaClass.simpleName)
            null
        }
    }

    private fun sha256(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
}
