# AI 기술 면접 준비 노트

> DevQuest 프로젝트에서 구현한 AI 관련 기술 정리.
> "AI 관련해서 뭘 해봤냐" 질문 대응용.

---

## 1. 프로젝트 개요

5년차 백엔드 개발자의 이직 준비를 RPG 퀘스트로 구성한 풀스택 앱.
자기소개서, 이력서, 기술 면접, 코딩 테스트 등 이직 준비 과정을 18개 AI 평가기로 채점하고 경험치와 점수를 부여한다.

---

## 2. 기술 스택

| 레이어 | 기술 |
|--------|------|
| LLM 제공자 | Anthropic Claude (Haiku 4.5, Sonnet 4.6) |
| AI 프레임워크 | Spring AI (spring-ai-starter-model-anthropic) |
| 코드 실행 | Judge0 CE API (코딩 문제 채점) |
| 메트릭 | Micrometer + OTLP (ai.call.total, ai.tokens.input/output) |
| 로깅 | AiCallLog 도메인 모델 → DB 저장 |
| 언어/프레임워크 | Kotlin / Spring Boot 4.x |

---

## 3. 아키텍처 설계 결정

### Port & Adapter 패턴으로 AI 제공자 추상화

```
core-domain (순수 Kotlin)
├── port/
│   ├── EssayEvaluatorPort       ← 인터페이스 (Spring 어노테이션 없음)
│   ├── JdAnalysisEvaluatorPort
│   ├── MockInterviewEvaluatorPort
│   └── ... (18개)
└── model/evaluation/
    ├── EssayCheckResult
    ├── JdAnalysisResult
    └── ... (결과 data class)

client-ai (Spring AI 의존)
└── evaluator/
    ├── CareerEssayEvaluator     ← Port 구현체 (@Component)
    ├── JdAnalysisEvaluator
    └── ...
```

**설계 이유**: 도메인이 Anthropic에 직접 의존하지 않아 제공자 교체 가능. 테스트 시 Mock adapter 주입이 단순해진다.

---

### bossChatClient (Sonnet) vs 기본 chatClient (Haiku) 분리

```kotlin
// AiClientConfig.kt
@Bean @Primary
fun chatClient(chatModel: ChatModel): ChatClient
    // → claude-haiku-4-5 (기본, 속도/비용 최적)

@Bean("bossChatClient")
fun bossChatClient(...): ChatClient
    // → claude-sonnet-4-6 (복잡한 분석 전용)
```

| 모델 | 담당 평가기 |
|------|-----------|
| Haiku | 이력서, 자기소개서, 기술스택 진단, 기술면접, 블로그 평가 |
| Sonnet | Boss패키지(포트폴리오 종합), 회사적합도, 모의면접, 면접코치, 여정리포트 |

**설계 이유**: 구조화된 JSON 출력은 Haiku로 충분. 다관점 종합 분석은 Sonnet 필요. 모델별 비용 10배 차이 → 작업별 분리로 비용 최적화.

---

### CallAdvisor 패턴으로 횡단 관심사 분리

```kotlin
// CacheMetricsAdvisor.kt — Spring AI CallAdvisor 구현
override fun adviseCall(request: ChatClientRequest, chain: CallAdvisorChain): ChatClientResponse {
    val startMs = System.currentTimeMillis()
    val response = chain.nextCall(request)  // 실제 AI 호출
    val latencyMs = System.currentTimeMillis() - startMs

    // 토큰 사용량 추출 (캐시 히트/미스 포함)
    val cacheRead = usage.cacheReadInputTokens()
    val cacheCreation = usage.cacheCreationInputTokens()

    // DB 기록
    aiCallLogPort.record(AiCallLog(evaluatorName, modelName, inputTokens, outputTokens,
        cacheRead, cacheCreation, latencyMs, success = true))

    // Micrometer 메트릭
    meterRegistry.counter("ai.call.total", "evaluator", evaluatorName).increment()
    meterRegistry.timer("ai.call.duration", "evaluator", evaluatorName).record(latencyMs, MILLISECONDS)
}
```

**장점**: 평가기 개발자는 AI 호출 로직만 집중. 로깅/메트릭/캐싱은 Advisor가 투명하게 처리.

---

### ThreadLocal 기반 평가기 추적

```kotlin
// AiCallContext.kt
object AiCallContext {
    private val context = ThreadLocal<String>()
    fun set(name: String) = context.set(name)
    fun get(): String = context.get() ?: "Unknown"
    fun clear() = context.remove()
}

// AiCallExecutor.kt
fun <T> execute(evaluatorName: String, action: () -> T?): T {
    AiCallContext.set(evaluatorName)   // 스레드에 평가기 이름 저장
    try {
        repeat(maxRetry) { attempt ->
            val result = action()
            if (result != null) return result
        }
        throw AiEvaluationException("${maxRetry}회 시도 후 최종 실패")
    } finally {
        AiCallContext.clear()
    }
}
```

**목적**: CacheMetricsAdvisor가 어떤 평가기의 호출인지 파악해 메트릭 태깅. 평가기 이름을 파라미터로 전달하지 않아도 됨.

---

## 4. 구현한 AI 기능 (18개 평가기)

### 이직 준비 평가 (4개)

| 평가기 | 입력 | 핵심 평가 축 | 출력 |
|--------|------|------------|------|
| CareerEssayEvaluator | 불만족 사유, 이직 목표, 5년 비전 | 명확성(30), 논리성(30), 진정성(20), 성장방향(20) | EssayCheckResult |
| ResumeCheckEvaluator | 이력서 텍스트 | STAR 기법(40), 수치화(30), 키워드 매칭(30) | ResumeCheckResult |
| SkillAssessmentEvaluator | 보유 기술, 목표 포지션 | 기술 다양성/깊이(30), 시장 적합성(30), 일관성(20), 성장성(20) | SkillAssessmentResult |
| DeveloperClassEvaluator | SkillAssessment + CareerEssay JSON | 기술수준 + 동기명확성 + 전략일관성 종합 | DeveloperClassResult |

---

### 채용공고 / 포트폴리오 (3개)

| 평가기 | 입력 | 핵심 기능 |
|--------|------|---------|
| JdAnalysisEvaluator | JD 텍스트 + 개발자 프로필 | 필수/선호 기술 추출, 숨은 요구사항 분석, 갭 점수화 |
| BossPackageEvaluator (Sonnet) | 이력서 + GitHub URL + 블로그 URL + 목표 포지션 | 5축 20점씩: 이력서 임팩트, GitHub 일관성, 기술 깊이, 포지션 핏, 차별화 |
| CompanyFitEvaluator (Sonnet) | 개발자 선호도 + 회사 리스트 | 문화(25), 기술(25), 성장(25), 라이프스타일(25) 적합도 |

---

### 면접 준비 (5개)

| 평가기 | 역할 |
|--------|------|
| TechInterviewEvaluator | 기술 면접 질문 생성 + 답변 채점 |
| PersonalityInterviewEvaluator | 인성 면접 답변 평가 (진정성, 팀워크, 커뮤니케이션) |
| MockInterviewEvaluator (Sonnet) | 연차/스택별 맞춤 질문 생성 + STAR 기법 기반 채점 |
| InterviewCoachEvaluator (Sonnet) | 3단계 코칭: ① JD 분석→질문 5개 생성, ② 답변별 피드백, ③ 세션 종합 리포트 |
| SystemDesignEvaluator | 시스템 설계 평가 (요구사항 분석, 확장성, 데이터 모델링, 가용성, 실현 가능성) |

**MockInterview 채점 기준 (100점)**:
```
기술적 정확성 40 + 깊이와 응용력 30 + 실무 경험 연결 20 + 커뮤니케이션 10
```

**InterviewCoach 최종 리포트 출력**:
```kotlin
data class CoachReportResult(
    val overallScore: Int,       // 전체 세션 점수
    val passLikelihood: Int,     // 합격 가능성 % (현실적 평가)
    val strengths: List<String>,
    val weaknesses: List<String>,
    val finalAdvice: String      // 실천 가능한 조언
)
```

---

### 코딩 (3개)

| 평가기 | 역할 |
|--------|------|
| CodingProblemGeneratorEvaluator | 난이도(EASY/MEDIUM/HARD)/언어(Java·Kotlin)/카테고리별 문제 생성 — 완전한 실행 코드 + 테스트케이스 포함 |
| CodingHintEvaluator | 레벨 1~3 단계적 힌트 (정답 코드 없이 사고 방향만) |
| Judge0Adapter | 외부 코드 실행 서비스 — 생성된 테스트케이스로 제출 코드 검증 |

**코딩 파이프라인**:
```
AI 문제 생성 → 사용자 코드 제출 → Judge0 실행 → 테스트케이스 통과 여부 → XP 지급
                                     ↑
                              실패 시 AI 힌트 요청
```

---

### 리포트 생성 (3개)

| 평가기 | 역할 |
|--------|------|
| ActClearReportEvaluator | Act(챕터) 내 여러 퀘스트 점수 → 종합 분석 + 다음 방향 제시 |
| JourneyReportGenerator (Sonnet) | 전체 퀘스트 완료 후 최종 이직 전략 리포트 생성 |
| TechBlogEvaluator | 기술 블로그 주제 깊이, 설명력, 실무 연결성 평가 |

---

## 5. 프롬프트 엔지니어링 전략

### 파일 기반 외부 관리

모든 프롬프트는 `resources/prompts/` 하위 `.st` (StringTemplate) 파일로 관리.
평가기당 `*-system.st` + `*-user.st` 쌍 구성 — 총 44개 파일.

```
prompts/
├── career-essay-system.st      ← 역할 정의 + 평가 루브릭 + JSON 형식 강제
├── career-essay-user.st        ← {dissatisfactions} {goals} {fiveYearVision} 변수
├── boss-package-system.st
├── boss-package-user.st
└── ... (총 44개)
```

**장점**: 프롬프트 버전 관리 가능, Git diff로 변경 추적, 코드 변경 없이 프롬프트 수정.

---

### 시스템 프롬프트 설계 패턴

**① 역할 정의 (Persona)**
```
"당신은 이직 준비 중인 백엔드 개발자의 자기소개서를 평가하는 전문가입니다."
"당신은 10년 이상의 경험을 가진 시니어 개발자 면접관 겸 커리어 코치입니다."
```

**② 평가 루브릭 명시**
```
## 평가 기준 (총 100점)
- 명확성 (30점): 불만족 사유와 목표가 구체적인가
- 논리성 (30점): 현재 상황 → 이직 → 미래 비전의 흐름이 자연스러운가
- 동기 진정성 (20점): 성장 욕구가 진정성 있게 느껴지는가
- 성장 방향 (20점): 5년 후 비전이 현실적이고 구체적인가
```

**③ JSON 출력 강제**
```
반드시 다음 JSON 형식으로만 응답하세요 (다른 텍스트 금지):
{
    "score": 75, "passed": true, "grade": "B",
    "clarityScore": 22, ...
}
```

**④ 연차별 평가 기준 분기**
```
## 연차별 채점 기준
- 신입~1년차: 기초 개념 이해도 우선
- 1-3년차: 기초 심화 + 문제 해결 능력
- 3-5년차: 설계 판단 능력 + 실무 경험의 질
- 5년차 이상: 아키텍처 수준 사고 + 트레이드오프 분석 능력
```

**⑤ Few-shot 예시 (JD 분석)**
```json
{
    "skill": "Spring Boot",
    "required": true,
    "userLevel": "상",
    "importance": "HIGH"
}
```

---

## 6. 토큰 최적화 & 비용 관리

### Anthropic Prompt Caching

```kotlin
// AiClientConfig.kt
val cacheOptions = AnthropicCacheOptions.builder()
    .strategy(AnthropicCacheStrategy.SYSTEM_ONLY)  // 시스템 프롬프트만 캐싱
    .build()
```

- **조건**: 2,000 토큰 이상 시스템 프롬프트에 자동 적용
- **효과**: 동일 평가기 반복 호출 시 입력 토큰 50~90% 절감
- **추적**: `cacheReadInputTokens` / `cacheCreationInputTokens` DB 기록 → 실제 절감량 측정 가능

### 모델 선택 전략

| 작업 유형 | 모델 | 이유 |
|---------|------|------|
| 구조화된 JSON 출력, 단순 채점 | Haiku | 빠른 응답, 낮은 비용 |
| 다관점 종합 분석, 코칭 리포트 | Sonnet | 복잡한 추론 필요 |

### AiCallLog 기반 비용 추적

```kotlin
data class AiCallLog(
    val evaluatorName: String,
    val modelName: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val cacheReadTokens: Int,       // 캐시 절감 토큰
    val cacheCreationTokens: Int,   // 캐시 생성 비용
    val latencyMs: Long,
    val success: Boolean,
)
```

모든 AI 호출을 DB에 저장 → 평가기별 비용/성능 분석 가능.

---

## 7. 응답 파싱 전략 (3가지)

### ① `.entity()` 자동 역직렬화

```kotlin
chatClient.prompt()
    .system(systemPrompt)
    .user(userPrompt)
    .call()
    .entity(EssayCheckResult::class.java)
```

Spring AI가 JSON 응답 → 객체 자동 변환. 가장 단순한 방식.

---

### ② 마크다운 코드블록 제거 후 Jackson 파싱

```kotlin
// BaseAiEvaluator.kt
protected fun <T> parseContent(content: String?, targetClass: Class<T>): T? {
    val raw = content?.trim() ?: return null
    val codeBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
    val json = codeBlockRegex.find(raw)?.groupValues?.get(1)?.trim() ?: raw
    return objectMapper.readValue(json, targetClass)
}

// 사용 예
val content = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content()
parseContent(content, ResumeCheckResult::class.java)
    ?.let { it.copy(passed = PassCriteriaPolicy.evaluate(it.overallScore)) }
```

AI가 응답을 `` ```json ... ``` `` 으로 래핑할 때 대응. 파싱 후 비즈니스 로직(PassCriteria) 추가 적용 가능.

---

### ③ 재시도 로직 (AiCallExecutor)

```kotlin
@Component
class AiCallExecutor(
    @Value("\${devquest.ai.max-retry:3}") private val maxRetry: Int
) {
    fun <T> execute(evaluatorName: String = "Unknown", action: () -> T?): T {
        AiCallContext.set(evaluatorName)
        try {
            var lastException: Exception? = null
            repeat(maxRetry) { attempt ->
                try {
                    val result = action()
                    if (result != null) return result
                } catch (e: Exception) {
                    lastException = e
                }
            }
            throw AiEvaluationException("${maxRetry}회 시도 후 최종 실패", lastException)
        } finally {
            AiCallContext.clear()
        }
    }
}
```

null 응답 또는 파싱 실패 시 최대 3회 자동 재시도.

---

## 8. 테스트 전략

### RETURNS_DEEP_STUBS 패턴

ChatClient는 빌더 체이닝이 깊어서 일반 mock으로는 설정이 복잡하다.

```kotlin
@ExtendWith(MockitoExtension::class)
class BossPackageEvaluatorTest {
    // RETURNS_DEEP_STUBS: 체이닝 호출(.prompt().system().user().call().entity())을 자동 mock 처리
    private val chatClient: ChatClient = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val aiCallExecutor = AiCallExecutor(maxRetry = 1)
    private val evaluator = BossPackageEvaluator(chatClient, aiCallExecutor)

    @Test
    fun `AI가 null을 반환하면 AiEvaluationException 발생`() { ... }

    @Test
    fun `AI가 정상 응답을 반환하면 결과를 그대로 반환`() { ... }

    @Test
    fun `사용자 프롬프트에 필수 정보가 포함된다`() { ... }
}
```

**금지 패턴**: Evaluator 테스트에서 `@Mock` + `@InjectMocks` 조합 금지 → `RETURNS_DEEP_STUBS`로 직접 생성.

---

### 실제 API 통합 테스트

```kotlin
// @Tag("integration") 분리 → ./gradlew :clients:client-ai:integrationTest 로만 실행
@Tag("integration")
class AiIntegrationTest {
    // ANTHROPIC_API_KEY 미설정 시 자동 스킵

    @Test fun `자기소개서 평가 — 점수 범위와 JSON 파싱 검증`()
    @Test fun `기술 스택 평가 — 5년차 백엔드 개발자 시나리오`()
    @Test fun `이력서 평가 — STAR 기법 및 수치화 검증`()
    @Test fun `JD 분석 — 필수 스킬 추출 및 매칭 점수 검증`()
    @Test fun `모의 면접 평가 — 5년차 기술 질문 채점`()
}
```

단위 테스트: 15개 평가기 각 3개 케이스 = 약 45개.
통합 테스트: 실제 API 호출 5개.

---

## 9. 비즈니스 도메인 문제 해결

**문제**: "이직 준비를 어떻게 하면 되는가?"가 추상적이다. 무엇을 얼마나 했는지 측정이 안 된다.

**해결**: 이직 준비 과정을 18개 퀘스트로 분해 → 각 퀘스트를 AI가 채점 → 점수에 따라 XP 지급 → 캐릭터 레벨업.

```
자기소개서 작성 (Quest 1-1) → AI 채점 70점 이상 → 통과 → 350 XP
이력서 개선 (Quest 1-2)     → AI 채점 점수 비례 XP → 다음 퀘스트 해금
JD 분석 (Quest 2-1)        → AI 분석 → 숨은 요구사항 파악 → 400 XP
...
포트폴리오 종합 (BOSS 2)    → Sonnet 5축 평가 → 800 XP
```

**결과**: 추상적인 이직 준비 → 측정 가능한 퀘스트 클리어율로 변환.

**XP 계산 정책**:
```kotlin
object QuestXpPolicy {
    fun calculate(questId: String, passed: Boolean, score: Int = 0, xpMultiplier: Double = 1.0): Int {
        if (!passed) return 0
        return when (questId) {
            "1-2" -> base * score / 100          // 점수 비례
            "2-1", "3-1" -> (base * xpMultiplier).toInt()  // 난이도 배수 적용
            else -> base                          // 고정
        }
    }
}
```

---

## 10. 기술적 챌린지 & 해결

### ① AI 응답 마크다운 래핑 문제

**상황**: 프롬프트에서 "JSON만 출력하라"고 지시해도 일부 응답이 `` ```json ... ``` `` 으로 래핑되어 Jackson 파싱 실패.

**해결**: `parseContent()` 함수에 마크다운 코드블록 제거 정규식 추가.
```kotlin
val codeBlockRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
val json = codeBlockRegex.find(raw)?.groupValues?.get(1)?.trim() ?: raw
```

---

### ② Spring Boot 4.x Jackson 혼재 (현재 진행 중)

**상황**: `client-ai` 모듈 일부 평가기가 Spring AI의 내장 Jackson 2.x를 사용. Spring Boot 4.x는 Jackson 3.x (tools.jackson) 기반.

**해결 방향**: `CompanyFitEvaluator`, `MockInterviewEvaluator` → `tools.jackson` ObjectMapper로 마이그레이션 중.

---

### ③ 코딩 문제 자동 생성 → 검증 파이프라인

**상황**: AI가 생성한 코딩 문제의 솔루션 코드와 테스트케이스가 실제로 통과하는지 보장이 안 됨.

**해결**: AI 생성 문제를 Judge0에 먼저 실행해 솔루션 코드가 자체 테스트케이스를 통과하는지 확인. 실패하면 재생성 요청.

```
CodingProblemGeneratorEvaluator
    → Judge0로 solutionCode 실행
    → testCases 전체 통과 확인
    → 통과 시에만 DB 저장
```

---

## 11. Claude Code 하네스 직접 구성

> "AI를 사용한 기능 구현" 외에, **AI 코딩 도구 자체를 어떻게 운영하는지**도 설계했다.

### 멀티 에이전트 시스템 아키텍처

```
orchestrator
├── be-feature-builder   — Kotlin/Spring Boot 구현 전담
├── design-reviewer      — UI 스펙 작성 (코드 수정 금지)
├── fe-feature-builder   — React/TypeScript 구현 전담
├── qa-reviewer          — 코드 리뷰 (코드 수정 금지)
└── pair-programmer      — 학습 모드 (방향 제시만, 코드 작성 금지)
```

**역할 경계 강제 메커니즘**:

| 에이전트 | 쓰기 허용 경로 | permissionMode |
|---------|-------------|----------------|
| orchestrator | `.claude/`만 | 기본 |
| be-feature-builder | `be/`만 | 기본 |
| fe-feature-builder | `fe/`만 | 기본 |
| design-reviewer | 수정 불가 | plan |
| qa-reviewer | 수정 불가 | plan |

각 에이전트 정의 파일의 `hooks.PreToolUse` + Shell 스크립트로 경로 벗어나면 차단.

---

### Orchestrator 9단계 워크플로우

```
0. CONTEXT.md 작업 시작 기록
1. 작업 분류 (feat / fix / refactor / chore / docs)
2. 필요 에이전트 선택 (BE만 / FE만 / BE+FE / 등)
3. 브랜치 생성 (origin/main 기준)
4. BE 구현 → API 스펙 추출
5. Design Spec 작성 (신규 UI 컴포넌트 시에만)
6. FE 구현
7. QA 리뷰 → CRITICAL 판단
8. CRITICAL 처리 (최대 2회 재시도)
9. PR 생성 + CONTEXT.md 업데이트 + push
```

**Disambiguation Gate**: 요청 의도 불명확 시 "가장 중요한 불확실성 1개"만 질문 후 진행.

---

### Hook 기반 자동화

| Hook | 스크립트 | 역할 |
|------|---------|------|
| SessionStart | `caveman-activate.sh` | Caveman 모드 자동 활성화 (토큰 ~65% 절감) |
| PreToolUse (Write/Edit) | `assert-not-main.sh` | main 브랜치 직접 수정 차단 |
| PreToolUse (Bash) | `assert-pr-reviewed.sh` | `gh pr create` 시 자동 AI 코드 리뷰 |
| SubagentStart/Stop | `log-event.sh` | 에이전트 생명주기 JSONL 기록 |
| Stop | `generate-report.js` | 세션 종료 활동 보고서 생성 |

---

### PR 사전 자동 리뷰 파이프라인

`gh pr create` 실행 시 `assert-pr-reviewed.sh`가 PreToolUse 훅으로 자동 실행된다.

```
gh pr create 시도
    ↓
HEAD SHA 캐시 확인 (.claude/review-cache/{SHA})
    ↓ 캐시 없으면
git diff HEAD~1..HEAD (15KB 제한)
    ↓
Anthropic API 직접 호출 (claude-haiku-4-5, max_tokens: 2000)
    ↓
CRITICAL 여부 판단
    ├── CRITICAL 있음 → exit 2 → gh pr create 차단
    └── CRITICAL 없음 → 캐시 저장 → PR 생성 진행
```

**리뷰 기준 (프롬프트 내장)**:
- core-domain에 Spring 어노테이션 금지
- db-core ↔ client-ai 직접 의존 금지
- `!!` 사용 금지
- FE: default export, CSS 모듈, Context 금지
- Evaluator 테스트: `@Mock/@InjectMocks` 금지

**CI yaml 대비 장점**: GitHub Actions 대기 없이 즉시 실행. 비용은 Haiku 모델로 최소화. 동일 SHA 재검토는 캐시로 skip.

---

### Skills 시스템 (universal vs project 분리)

```
skills/
├── universal/
│   ├── tdd.md                          — RED → GREEN → REFACTOR 강제
│   ├── systematic-debugging.md        — 원인 파악 우선 디버깅
│   ├── verification-before-completion.md — 완료 전 실증 검증
│   └── clarify-before-execute.md      — 모호한 요청 명확화
└── project/
    └── be-module-create.md             — 프로젝트 특화 모듈 생성
```

**작업 유형별 주입 규칙**:
| 작업 | 주입 스킬 |
|------|---------|
| 신규 기능 | `tdd` + `verification-before-completion` |
| 버그 수정 | `systematic-debugging` + `tdd` + `verification-before-completion` |
| 리팩토링 | `verification-before-completion`만 |

Orchestrator가 스폰 시 스킬 파일 내용을 프롬프트에 전문 주입 (생략 금지).

---

### 세션 컨텍스트 관리 (CONTEXT.md)

새 대화 시작 시 자동으로 읽어 이전 상태를 복원한다.

```
고정 섹션: 비자명적 결정 (예: "Spring Boot 4.x Flyway 수동 구성 이유")
동적 섹션:
  - 현재 상태: 브랜치명, 열린 PR 번호
  - 최근 완료: PR 번호/내용/날짜 (3건)
  - 다음 작업: 백로그
```

80줄 초과 시 오래된 항목을 `CONTEXT.archive.md`로 이동 — 컨텍스트 윈도우 낭비 방지.

---

### 토큰 효율화 규칙 (에이전트 공통)

| 상황 | 규칙 |
|------|------|
| Glob 결과 파일 읽기 | 4개 이하만 병렬 Read |
| Grep | `head_limit: 20` 제한 |
| 대용량 파일 | `offset + limit`으로 부분 읽기 |
| Bash 출력 | `tail -N`으로 마지막 N줄만 캡처 |

---

## 면접 핵심 답변 포인트

### "AI 어떻게 활용했나요?"
→ 이직 준비 과정을 18개 퀘스트로 분해, 각 퀘스트를 AI로 채점해 XP 부여. 단순 텍스트 분석이 아니라 비즈니스 로직(통과 기준, 등급, XP 계산)과 결합한 평가 시스템.

### "프롬프트 엔지니어링 어떻게 했나요?"
→ `.st` 파일로 외부 관리, 평가기별 루브릭 명시, JSON 강제 출력, 연차별 분기. 마크다운 래핑 대응 파싱 로직까지 구현.

### "비용 최적화는?"
→ Haiku/Sonnet 작업별 분리 + Prompt Caching (SYSTEM_ONLY) + AiCallLog DB 기록으로 실제 절감량 측정.

### "아키텍처 설계는?"
→ Port & Adapter로 AI 제공자 추상화. CallAdvisor 패턴으로 로깅/메트릭/캐싱 분리. 18개 평가기 모두 동일한 패턴 준수.

### "Claude Code 하네스 구성이란?"
→ 6개 전문화 에이전트 (역할 경계 Shell 스크립트로 강제) + PR 생성 시 자동 AI 리뷰 + SessionStart 훅으로 Caveman 모드 자동 활성화. 180+ PR 운영하며 개인 프로젝트에 엔터프라이즈급 자동화 적용.
