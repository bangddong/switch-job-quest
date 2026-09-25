package com.devquest.ai.config

import com.devquest.ai.stub.TechInterviewStubEvaluator
import com.devquest.core.domain.port.TechInterviewPort
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * D-008 — 학습 클러스터 전용 AI 스텁 전환 스위치.
 *
 * core-api의 `AiTransportConfig`(inprocess ↔ http 전송 계층 전환)와 **정확히 같은 형태**다:
 * `@ConditionalOnProperty` + `@Primary`로 `client-ai`의 `@Component` 구현(`TechInterviewEvaluator`)을
 * 조건부로 덮어쓴다.
 *
 * - 기본값(프로퍼티 미설정, off): 이 클래스는 어떤 빈도 만들지 않는다 → `client-ai`의
 *   `TechInterviewEvaluator`(ai-api가 `implementation(project(":clients:client-ai"))`로 이미 의존)가
 *   `TechInterviewPort`의 유일한 구현으로 주입된다. **prod에 스텁이 조용히 켜지는 일이 없다** —
 *   "잊으면 터진다"가 아니라 "잊으면 안 돈다" 형태(열린 결정 ①과 동일 원칙).
 * - `devquest.ai.stub.tech-interview.enabled=true`: 이 빈이 `@Primary`로 등록돼
 *   `TechInterviewStubEvaluator`가 주입된다.
 *
 * 🔴 B-2b 함정 재확인: `@ConditionalOnProperty` 게이트가 닫혔을 때 폴백이 없으면 기동 실패로
 * 이어진다(daily-api가 실제로 겪은 문제). 여기서는 ai-api가 `client-ai`를 항상 의존하므로
 * `client-ai`의 `@Component` 구현이 게이트가 닫혀 있어도 항상 폴백으로 존재해 안전하다.
 *
 * `TechInterviewPort` 하나만 스텁한다 — 나머지 16개 `AiEvaluatorPort` 하위 타입은 스텁 대상이 아니며
 * 여전히 실제 `ANTHROPIC_API_KEY`가 필요하다. `Judge0Port`도 스텁되지 않는 것은 같지만 필요한 키가
 * 다르다 — `ANTHROPIC_API_KEY`가 아니라 `JUDGE0_API_KEY`(`devquest.judge0.api-key` 프로퍼티 →
 * RapidAPI `X-RapidAPI-Key` 헤더)다. 근거: `Judge0Adapter.kt:12,23,37`(client-ai 모듈).
 *
 * ⚠️ **`AI 포트`라는 표현을 쓰지 말 것 — 이 레포에서 두 뜻으로 쓰인다.** 숫자를 셀 때는 인터페이스
 * 이름(`AiEvaluatorPort` / `Judge0Port`)을 직접 써라.
 *   - `AiEvaluatorPort` 하위 타입 = **17개**(`TechInterviewPort` 포함). **기계로 고정되는 것은 이
 *     숫자 하나뿐**이다 — `core-domain` 모듈 test 소스셋의
 *     `com.devquest.core.domain.port.ArchAiPortConventionTest`가 `hasSize(17)`로 단언한다.
 *     `TechInterviewPort`를 뺀 나머지는 16개.
 *   - `AiEvaluatorPort`(17) + `Judge0Port`를 합쳐 "18"이라 부르는 것은 **테스트가 검증하지 않는
 *     관례**일 뿐이다 — `BaseAiHttpAdapter.kt:14`, `AiTransportConfig.kt:65`,
 *     `AiTransportSwitchTest.kt:69` 세 곳에서 그렇게 쓴다. 이 관례는 레포 안에서도 안 지켜진다 —
 *     `Judge0HttpAdapter.kt:13`은 "17개 AI 포트"로 `Judge0Port`를 **뺀** 범위를 가리켜, 같은 표현이
 *     파일마다 다른 모집단을 가리킨다. **`18`을 `17`과 같은 권위로 적지 말 것.**
 * 과거 이 구분을 놓쳐 `17`로 잘못 적혀 있었다(원장 L-64).
 */
@Configuration
class AiStubConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "devquest.ai.stub.tech-interview", name = ["enabled"], havingValue = "true")
    fun techInterviewStubEvaluator(): TechInterviewPort = TechInterviewStubEvaluator()
}
