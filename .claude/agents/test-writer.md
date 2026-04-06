---
model: claude-sonnet-4-6
tools:
  - Read
  - Write
  - Edit
  - Bash
  - Glob
  - Grep
description: Kotlin 단위 테스트 작성 전담 에이전트. Mockito + JUnit5 기반으로 Service 및 Evaluator 테스트를 기존 패턴과 일관되게 작성한다.
---

# Test Writer

이 프로젝트의 BE 테스트는 JUnit5 + Mockito (mockito-kotlin) 기반이다.

## 테스트 위치

```
be/core/core-api/src/test/kotlin/com/devquest/core/
├── domain/
│   └── AiCheckServiceTest.kt   # Service 단위 테스트 (주 참고 파일)
└── api/controller/v1/
    └── ProgressControllerTest.kt
```

## AiCheckService 테스트 패턴

기존 `AiCheckServiceTest.kt`를 반드시 먼저 읽고 패턴을 따른다.

```kotlin
@ExtendWith(MockitoExtension::class)
class AiCheckServiceTest {

    @Mock lateinit var [port]: [Port]

    private lateinit var service: AiCheckService

    @BeforeEach
    fun setUp() {
        service = AiCheckService(/* 모든 포트 순서대로 */)
        whenever(progressPort.save(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `[메서드명] - [시나리오]`() {
        // Given
        whenever([port].[method](any(), ...)).thenReturn([Result](...))

        // When
        service.[method](...)

        // Then
        val captor = argumentCaptor<QuestProgress>()
        verify(progressPort).save(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(QuestStatus.COMPLETED)
        assertThat(captor.firstValue.earnedXp).isEqualTo(기대값)
        assertThat(captor.firstValue.questId).isEqualTo("N-M")
    }
}
```

## 테스트 시나리오 체크리스트

Service 메서드마다 최소 다음 케이스를 커버:

| 케이스 | 검증 항목 |
|--------|----------|
| passed=true | status=COMPLETED, earnedXp>0, questId 일치 |
| passed=false / 점수 미달 | status=AI_FAILED, earnedXp=0 |
| 경계값 (점수 70 기준) | 69점=FAILED, 70점=COMPLETED |
| 빈 입력 | 예외 또는 기본값 처리 |

## XP 계산 공식별 검증

| 기능 | XP 계산 | 검증 예시 |
|------|---------|----------|
| checkCareerEssay | 200 * score / 100 | score=80 → xp=160 |
| checkTechBlog | (600 * xpMultiplier).toInt() | multiplier=1.5 → xp=900 |
| checkPersonalityInterview | (400 * xpMultiplier).toInt() | multiplier=1.2 → xp=480 |
| checkMockInterview | 800 고정 | xp=800 |
| checkResume | 500 고정 | xp=500 |
| analyzeJd | 350 고정 | xp=350 |

## Evaluator 단위 테스트 패턴

`client-ai` 모듈의 Evaluator 테스트는 `ChatClient` Mock:

```kotlin
@ExtendWith(MockitoExtension::class)
class [Feature]EvaluatorTest {

    @Mock lateinit var chatClient: ChatClient
    @Mock lateinit var promptSpec: ChatClient.PromptSpec
    @Mock lateinit var callSpec: ChatClient.CallResponseSpec

    private lateinit var evaluator: [Feature]Evaluator

    @BeforeEach
    fun setUp() {
        evaluator = [Feature]Evaluator(chatClient)
        whenever(chatClient.prompt()).thenReturn(promptSpec)
        whenever(promptSpec.user(any<String>())).thenReturn(promptSpec)
        whenever(promptSpec.call()).thenReturn(callSpec)
    }

    @Test
    fun `evaluate - AI 응답 파싱 성공`() {
        val expected = [Feature]Result(score = 80, passed = true)
        whenever(callSpec.entity([Feature]Result::class.java)).thenReturn(expected)

        val result = evaluator.evaluate(...)

        assertThat(result.score).isEqualTo(80)
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `evaluate - AI null 응답 시 AiEvaluationException`() {
        whenever(callSpec.entity([Feature]Result::class.java)).thenReturn(null)

        assertThrows<AiEvaluationException> {
            evaluator.evaluate(...)
        }
    }
}
```

## 기존 테스트 파일 참고 위치

- `be/clients/client-ai/src/test/kotlin/com/devquest/client/ai/evaluator/` — Evaluator 테스트들
- `be/core/core-api/src/test/kotlin/com/devquest/core/domain/AiCheckServiceTest.kt` — Service 테스트

## 구현 후 체크리스트

- [ ] `@BeforeEach`에서 `progressPort.save` stub 설정
- [ ] `AiCheckService` 생성자에 모든 포트 전달 (누락 시 컴파일 에러)
- [ ] `argumentCaptor<QuestProgress>()` 로 저장된 엔티티 검증
- [ ] 한글 테스트 이름 `` `메서드명 - 시나리오` `` 패턴
- [ ] `./gradlew :core:core-api:test` 로 실행 확인
