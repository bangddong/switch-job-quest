package com.devquest.daily

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication

/**
 * Phase 2 Stage B-2b — `core:daily-core`(콘텐츠 생성 로직) · `clients:client-ai-http`(AI 전송) ·
 * `storage:db-core`(저장)를 조립해 단독으로 기동하는 데일리 질문 전용 앱.
 *
 * `scanBasePackages = ["com.devquest"]`: 이 클래스가 속한 `com.devquest.daily` 패키지만 스캔하면
 * `com.devquest.storage.db.core`(db-core 어댑터·설정)와 `com.devquest.core.domain`(daily-core
 * 서비스)의 `@Component`/`@Service`가 빈으로 잡히지 않는다 — core-api(`DevQuestApplication`),
 * ai-api(`AiApiApplication`)와 동일한 패턴.
 *
 * `@EntityScan("com.devquest.storage.db.core")`가 **반드시 명시적으로 필요한 이유(함정 ②,
 * 사전 조사에서 레포 전체 `@EntityScan` 0건 확인)**: `@SpringBootApplication`의
 * `@EnableAutoConfiguration`이 부여하는 `@AutoConfigurationPackage`(엔티티 스캔 기준 패키지)는
 * `scanBasePackages`가 아니라 **이 애플리케이션 클래스가 위치한 패키지**(`com.devquest.daily`)로
 * 고정된다. `scanBasePackages`는 컴포넌트 스캔(빈 탐색) 범위만 넓힐 뿐 엔티티 스캔 기준에는
 * 영향을 주지 않는다. core-api는 `DevQuestApplication`이 애초에 루트 패키지 `com.devquest`에
 * 있어서 이 문제를 밟은 적이 없고, ai-api는 JPA를 쓰지 않아 선례가 없다. 이걸 빠뜨리면
 * 레포지토리 빈(`DailyQuestionContentPort`·`TechQuestionBankPort` 구현체)은 등록되는데 엔티티가
 * 0개로 로드되어 `Not a managed type: DailyQuestionContentEntity`로 기동 실패한다.
 *
 * `@EnableJpaRepositories`는 별도로 선언하지 않는다 — db-core의
 * `com.devquest.storage.db.core.config.CoreJpaConfig`가 이미
 * `@EnableJpaRepositories(basePackages = ["com.devquest.storage.db.core"])`를 갖고 있고,
 * 이 클래스는 컴포넌트 스캔(`scanBasePackages`)으로 빈으로 잡힌다 — 중복 선언 시 설정 충돌 위험.
 */
@SpringBootApplication(scanBasePackages = ["com.devquest"])
@EntityScan("com.devquest.storage.db.core")
class DailyApiApplication

fun main(args: Array<String>) {
    runApplication<DailyApiApplication>(*args)
}
