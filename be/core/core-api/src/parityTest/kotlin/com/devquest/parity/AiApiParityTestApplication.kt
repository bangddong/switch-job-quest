package com.devquest.parity

import com.devquest.ai.AiApiApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

/**
 * Phase 1 Task 1.5 parity 테스트 전용 부트스트랩 — `com.devquest.ai.AiApiApplication`을 재사용하지
 * 않는 이유를 기록한다.
 *
 * `AiApiApplication`은 `scanBasePackages = ["com.devquest"]`로 넓게 스캔한다(client-ai 평가자가
 * `com.devquest.client.ai`에 있어 기본 스캔 범위 `com.devquest.ai` 밖이기 때문 — 그 클래스의 KDoc
 * 참고). 이 테스트는 core-api 모듈의 테스트 소스셋 안에서 실행되는데(`testImplementation(project(
 * ":core:ai-api"))`), "com.devquest" 전체를 스캔하면 core-api 자신의 컨트롤러·서비스·보안 설정
 * (JWT·GitHub OAuth·메일 발송·DB 어댑터 등)까지 같은 컨텍스트에 딸려 들어온다 — 실제 ai-api
 * 프로세스에서는 core-api 클래스가 클래스패스에 아예 없으므로 절대 일어나지 않는 컨텍스트 오염이다.
 *
 * 그래서 ai-api가 실제로 필요로 하는 정확히 두 패키지만 스캔한다:
 * - `com.devquest.ai` — ai-api의 컨트롤러·`AiCallLogObservabilityAdapter`
 * - `com.devquest.client.ai` — client-ai의 평가자(`@Component`)·`AiCallExecutor`·`AiClientConfig`·
 *   `CacheMetricsAdvisor` 등
 *
 * 두 패키지 모두 파일 전수 확인 결과 하위 클래스가 이 루트 밖으로 새는 경우가 없다(grep으로 확인).
 * 이렇게 하면 **프로덕션과 동일한 빈 그래프**(같은 컨트롤러·같은 평가자·같은 설정 클래스)를 그대로
 * 재현하면서 core-api 자신의 컴포넌트만 배제할 수 있다 — "차선책"이 아니라 테스트 격리를 위한
 * 스캔 범위 조정일 뿐, 실제로 검증하는 빈 그래프는 ai-api 프로덕션과 동일하다.
 *
 * ⚠️ **`AiApiApplication` 자체를 명시적으로 제외한다(실측으로 발견한 함정).** `com.devquest.ai`를
 * 스캔 대상에 넣으면 `AiApiApplication` 클래스 자신도 `@Component`(정확히는 `@SpringBootConfiguration`
 * → `@Configuration`) 후보로 함께 발견된다. Spring은 스캔으로 찾은 `@Configuration` 클래스도
 * `ConfigurationClassParser`로 마저 처리하므로, `AiApiApplication`이 갖고 있는 자기 자신의
 * `@ComponentScan(basePackages=["com.devquest"])`가 **다시 한번, 이번엔 훨씬 넓은 범위로** 재실행돼
 * `com.devquest.core.api.controller.v1.InterviewCoachController`(core-api 자신의 무관한 컨트롤러,
 * ai-api의 동명 컨트롤러와 빈 이름이 우연히 겹침)까지 끌려 들어와
 * `ConflictingBeanDefinitionException`으로 컨텍스트 로드가 실패했다(실측: 이 필터 없이 실행 시 재현).
 * 이 클래스를 제외하면 원래 의도한 두 패키지만 정확히 스캔된다.
 *
 * ⚠️ **Spring Security 오토컨피그도 명시적으로 끈다(실측으로 발견한 두 번째 함정).** 컴포넌트 스캔은
 * 패키지로 좁혔지만, Spring Boot의 **오토컨피그는 컴포넌트 스캔 범위와 무관하게 클래스패스 존재
 * 여부만으로 활성화**된다. `spring-boot-starter-security`는 core-api 자신의 의존성(실제 ai-api엔
 * 없음)인데 이 테스트가 core-api 테스트 클래스패스 안에서 실행되는 한 항상 거기 있다 — 그 결과
 * `SecurityAutoConfiguration`이 자동으로 모든 엔드포인트를 기본 HTTP Basic으로 보호해 모든 요청이
 * 401로 막혔다(실측 확인). 실제 ai-api 프로세스에는 이 스타터 자체가 클래스패스에 없으므로 이 동작은
 * 프로덕션에 존재하지 않는다 — 그래서 이 두 오토컨피그를 명시적으로 제외해 프로덕션과 동일한
 * "인증 없음" 상태를 재현한다.
 */
@SpringBootApplication(
    exclude = [
        SecurityAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
        ServletWebSecurityAutoConfiguration::class,
        ManagementWebSecurityAutoConfiguration::class,
    ],
)
@ComponentScan(
    basePackages = ["com.devquest.ai", "com.devquest.client.ai"],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = [AiApiApplication::class]),
    ],
)
class AiApiParityTestApplication
