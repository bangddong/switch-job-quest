package com.devquest.core.domain

import com.devquest.core.domain.port.UserEmailPort
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class DailyMailScheduler(
    private val userEmailPort: UserEmailPort,
    private val mailService: MailService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val techInterviewQuestions = listOf(
        "JVM의 가비지 컬렉션 동작 방식과 G1GC의 특징을 설명해주세요.",
        "Spring의 트랜잭션 전파 속성(Propagation)에 대해 설명해주세요.",
        "데이터베이스 인덱스의 동작 원리와 B-Tree 인덱스의 구조를 설명해주세요.",
        "RESTful API 설계 원칙과 멱등성(Idempotency)에 대해 설명해주세요.",
        "TCP와 UDP의 차이점 및 각각의 사용 사례를 설명해주세요.",
        "동시성 프로그래밍에서 발생하는 문제(Race Condition, Deadlock)와 해결 방법을 설명해주세요.",
        "캐시 전략(Cache-Aside, Write-Through, Write-Behind)에 대해 설명해주세요.",
        "MSA(마이크로서비스 아키텍처)의 장단점과 모놀리식과의 비교를 설명해주세요.",
        "SQL의 EXPLAIN을 활용한 쿼리 최적화 방법을 설명해주세요.",
        "JWT 인증 방식의 동작 원리와 보안 고려사항을 설명해주세요.",
    )

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    fun sendDailyTechInterviewMail() {
        val allUsers = userEmailPort.findAll()
        if (allUsers.isEmpty()) {
            log.info("발송 대상 없음 — 기술 면접 데일리 메일 skip")
            return
        }

        val question = techInterviewQuestions.random()
        val deepLink = "https://devquest.kr/tech-interview"

        log.info("데일리 기술 면접 메일 발송 시작: 대상 수=${allUsers.size}")
        allUsers.forEach { (userId, email) ->
            runCatching {
                mailService.sendDailyTechInterview(email, question, deepLink)
            }.onFailure { e ->
                log.warn("메일 발송 실패: userId=$userId, email=$email, error=${e.message}")
            }
        }
        log.info("데일리 기술 면접 메일 발송 완료")
    }
}
