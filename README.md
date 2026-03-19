# DevQuest — 개발자 이직 RPG

개발자의 이직 준비 과정을 RPG 퀘스트로 게이미피케이션한 풀스택 애플리케이션입니다.
AI(Claude)가 에세이, 이력서, 면접 등을 평가하고, 퀘스트 클리어를 통해 이직 준비 완성도를 높여갑니다.

---

## 목차

- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [퀘스트 시스템](#퀘스트-시스템)
- [AI 평가 시스템](#ai-평가-시스템)
- [API 명세](#api-명세)
- [데이터베이스](#데이터베이스)
- [실행 방법](#실행-방법)
- [설정](#설정)

---

## 기술 스택

### Backend

| 항목 | 버전 |
|------|------|
| Kotlin | 2.1.0 |
| Java | 21 |
| Spring Boot | 4.0.3 |
| Spring AI (Anthropic) | 2.0.0-M3 |
| Gradle | 9.0.0 |
| H2 Database | (in-memory) |

### Frontend

| 항목 | 버전 |
|------|------|
| React | 19.0.0 |
| TypeScript | 5.7.2 |
| Vite | 6.0.0 |

---

## 프로젝트 구조

```
switch-job-quest/
├── be/                                 # Backend (Kotlin / Spring Boot)
│   ├── core/
│   │   ├── core-enum/                  #   QuestStatus 열거형
│   │   ├── core-domain/                #   도메인 모델, Port 인터페이스, 평가 타입
│   │   └── core-api/                   #   REST 컨트롤러, 서비스, DTO (bootJar)
│   ├── clients/
│   │   └── client-ai/                  #   Spring AI 기반 평가기 구현 (Adapter)
│   ├── storage/
│   │   └── db-core/                    #   JPA 엔티티, 리포지토리, DB 설정
│   ├── support/
│   │   ├── logging/                    #   로깅 설정
│   │   └── monitoring/                 #   Prometheus, Health 엔드포인트
│   └── tests/
│       └── api-docs/                   #   REST Docs 테스트 지원
│
└── fe/                                 # Frontend (React / TypeScript)
    └── src/
        ├── app/                        #   App.tsx (루트 컴포넌트)
        ├── features/
        │   ├── quest-map/              #   퀘스트 맵, Act 카드, 통계 패널
        │   ├── quest-detail/           #   퀘스트 상세, Act 뷰
        │   └── ai-check/              #   AI 평가 폼, 결과 카드, 모의면접
        ├── components/ui/              #   GradeTag, ProgressBar, ScoreRing
        ├── types/                      #   Quest, API 타입 정의
        ├── hooks/                      #   useUserId
        ├── lib/                        #   apiClient (HTTP)
        └── styles/                     #   global.css (다크 테마)
```

### 아키텍처

**헥사고날 아키텍처 (Port & Adapter 패턴)**

```
Controller → Service → Port (interface) ← Adapter (implementation)
                                              ↓
                                     Spring AI / JPA
```

- **Port**: `core-domain`에 정의된 인터페이스 (예: `EssayEvaluatorPort`)
- **Adapter**: `client-ai`의 AI 평가기, `db-core`의 JPA 리포지토리

---

## 퀘스트 시스템

5개 Act, 18개 퀘스트로 구성된 이직 여정입니다.
각 Act는 이전 Act의 **75% 이상** 완료 시 해금됩니다.

### ACT I: 캐릭터 생성 ⚗️

| 퀘스트 | 타입 | 난이도 | XP | AI 평가 |
|--------|------|--------|-----|---------|
| 1-1: 기술 스택 자가 진단 | 📚 STUDY | ★☆☆☆☆ | 150 | - |
| 1-2: 이직 동기 에세이 | ✍️ WRITE | ★☆☆☆☆ | 200 | ✅ |
| 1-BOSS: 개발자 클래스 판별 | ⚔️ BOSS | ★★☆☆☆ | 500 | ✅ |

### ACT II: 스킬 강화 ⚔️

| 퀘스트 | 타입 | 난이도 | XP | AI 평가 |
|--------|------|--------|-----|---------|
| 2-1: 약점 기술 집중 공략 | 📚 STUDY | ★★★☆☆ | 400 | - |
| 2-2: 기술 블로그 검사 | ✍️ WRITE | ★★★☆☆ | 600 | ✅ |
| 2-3: 시스템 설계 챌린지 | ⚒️ BUILD | ★★★★☆ | 500 | ✅ |
| 2-4: 코딩 테스트 30문제 | ⚒️ BUILD | ★★★☆☆ | 450 | - |
| 2-BOSS: 모의 기술 면접 | ⚔️ BOSS | ★★★★★ | 800 | ✅ |

### ACT III: 세계 탐색 🗺️

| 퀘스트 | 타입 | 난이도 | XP | AI 평가 |
|--------|------|--------|-----|---------|
| 3-1: 관심 회사 10곳 리스트업 | 🔍 DISCOVER | ★★☆☆☆ | 250 | - |
| 3-2: JD 역분석 | 🔍 DISCOVER | ★★★☆☆ | 350 | ✅ |
| 3-BOSS: 최종 타겟 3곳 확정 | ⚔️ BOSS | ★★★☆☆ | 600 | - |

### ACT IV: 장비 제작 🛡️

| 퀘스트 | 타입 | 난이도 | XP | AI 평가 |
|--------|------|--------|-----|---------|
| 4-1: 이력서 STAR 검토 | ⚒️ BUILD | ★★★☆☆ | 500 | ✅ |
| 4-2: GitHub 프로필 리모델링 | ⚒️ BUILD | ★★☆☆☆ | 300 | - |
| 4-BOSS: 지원 패키지 완성 | ⚔️ BOSS | ★★★★☆ | 700 | ✅ |

### ACT V: 최종 보스전 👑

| 퀘스트 | 타입 | 난이도 | XP | AI 평가 |
|--------|------|--------|-----|---------|
| 5-1: 인성 면접 연습 | 📚 STUDY | ★★★☆☆ | 400 | ✅ |
| 5-2: 실전 지원 개시 | ⚒️ BUILD | ★★★☆☆ | 500 | - |
| 5-BOSS: 합격! | ⚔️ BOSS | ★★★★★ | 2,000 | - |

> **총 XP**: 9,150 | **AI 평가 퀘스트**: 10개

---

## AI 평가 시스템

Claude Sonnet 4.5를 사용하여 제출물을 평가합니다.
**통과 기준: 70점 이상** | 최대 재시도: 3회

### 평가 항목별 채점 기준

#### 이직 동기 에세이 (Quest 1-2)

| 기준 | 배점 | 설명 |
|------|------|------|
| 명확성 (Clarity) | 30점 | 구체적인 불만 사항과 목표 |
| 논리성 (Logic) | 30점 | 현재 → 이직 → 미래의 자연스러운 흐름 |
| 동기 진정성 (Motivation) | 20점 | 진정한 성장 욕구 |
| 성장 방향 (Growth Vision) | 20점 | 현실적이고 구체적인 5년 비전 |

#### 기술 블로그 (Quest 2-2)

| 기준 | 배점 |
|------|------|
| 기술적 정확성 | 40점 |
| 깊이와 통찰 | 25점 |
| 코드 예시 품질 | 20점 |
| 가독성과 구조 | 15점 |

#### 시스템 설계 (Quest 2-3)

| 기준 | 배점 |
|------|------|
| 요구사항 분석 | 20점 |
| 확장성 설계 | 25점 |
| 데이터 모델링 | 20점 |
| 고가용성/장애 대응 | 20점 |
| 실현 가능성 | 15점 |

#### 모의 기술 면접 (Quest 2-BOSS)

| 기준 | 배점 |
|------|------|
| 기술적 정확성 | 40점 |
| 깊이와 응용 | 30점 |
| 실무 경험 연결 | 20점 |
| 커뮤니케이션 | 10점 |

- 카테고리: DB, JVM, Network, OS, Design
- 문항 수: 10개 (랜덤)
- 통과 조건: 평균 70점 이상

#### 이력서 STAR 검토 (Quest 4-1)

| 기준 | 배점 |
|------|------|
| STAR 기법 활용 | 40점 |
| 정량화 수준 | 30점 |
| JD 키워드 매칭 | 30점 |

#### 인성 면접 (Quest 5-1)

| 기준 | 배점 |
|------|------|
| 구체성 | 30점 |
| 진정성 | 25점 |
| 성장 마인드셋 | 25점 |
| 소통 능력 | 20점 |

### 등급 체계

| 등급 | 점수 범위 |
|------|-----------|
| S | 90 ~ 100 |
| A | 80 ~ 89 |
| B | 70 ~ 79 |
| C | 60 ~ 69 |
| D | 0 ~ 59 |

---

## API 명세

**Base URL**: `http://localhost:8080`

### Health Check

```
GET /health
→ { success: true, result: "SUCCESS", data: "DevQuest API is running" }
```

### 퀘스트 진행도

```
GET /api/v1/progress/{userId}
→ QuestProgress[]
```

### AI 평가

| Method | Endpoint | 퀘스트 |
|--------|----------|--------|
| POST | `/api/v1/ai-check/career-essay` | 1-2: 이직 동기 에세이 |
| POST | `/api/v1/ai-check/tech-blog` | 2-2: 기술 블로그 |
| POST | `/api/v1/ai-check/system-design` | 2-3: 시스템 설계 |
| GET | `/api/v1/ai-check/mock-interview/questions` | 2-BOSS: 면접 문제 생성 |
| POST | `/api/v1/ai-check/mock-interview` | 2-BOSS: 면접 답변 평가 |
| POST | `/api/v1/ai-check/jd-analysis` | 3-2: JD 역분석 |
| POST | `/api/v1/ai-check/resume` | 4-1: 이력서 검토 |
| POST | `/api/v1/ai-check/company-fit` | 1-BOSS: 개발자 클래스 판별 |
| POST | `/api/v1/ai-check/personality-interview` | 5-1: 인성 면접 |

### 공통 응답 형식

성공:
```json
{
  "result": "SUCCESS",
  "data": { ... }
}
```

실패:
```json
{
  "result": "ERROR",
  "error": {
    "code": "INVALID_REQUEST",
    "message": "잘못된 요청입니다"
  }
}
```

에러 코드: `INVALID_REQUEST` | `AI_EVALUATION_FAILED` | `QUEST_NOT_FOUND` | `DEFAULT`

### AI 평가 결과 형식

```json
{
  "score": 82,
  "passed": true,
  "grade": "A",
  "summary": "전체 평가 요약",
  "strengths": ["강점 1", "강점 2"],
  "improvements": ["개선점 1", "개선점 2"],
  "detailedFeedback": "상세 피드백",
  "retryAllowed": true
}
```

---

## 데이터베이스

### quest_progress 테이블

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT (PK) | 자동 증가 |
| user_id | VARCHAR | 사용자 ID |
| quest_id | VARCHAR | 퀘스트 ID (예: "1-2") |
| act_id | INT | Act 번호 (1~5) |
| status | ENUM | NOT_STARTED, IN_PROGRESS, COMPLETED, AI_FAILED |
| ai_score | INT | AI 평가 점수 (0~100) |
| earned_xp | INT | 획득 XP |
| ai_evaluation_json | TEXT | AI 평가 전체 응답 (JSON) |
| completed_at | DATETIME | 완료 시각 |
| created_at | DATETIME | 생성 시각 (자동) |
| updated_at | DATETIME | 수정 시각 (자동) |

> 현재 H2 인메모리 DB 사용 (서버 재시작 시 초기화)

---

## 실행 방법

### 사전 요구사항

- **JDK 21** (Gradle toolchain이 자동 감지)
- **Node.js 18+**
- **Anthropic API Key**

### Backend 실행

```bash
cd be

# API 키 설정
export ANTHROPIC_API_KEY=your-api-key-here

# 빌드 & 실행
./gradlew build
./gradlew :core:core-api:bootRun
```

서버가 `http://localhost:8080`에서 시작됩니다.

로컬 개발 시 H2 콘솔, SQL 로깅 등을 활성화하려면 local 프로파일로 실행합니다.

```bash
./gradlew :core:core-api:bootRun --args='--spring.profiles.active=local'
```

> **참고**: Spring Boot 4.0에서 H2 콘솔 자동 등록(`H2ConsoleAutoConfiguration`)이 제거되어 현재 H2 콘솔은 비활성 상태입니다.

### Frontend 실행

```bash
cd fe

npm install
npm run dev
```

개발 서버가 `http://localhost:5173`에서 시작됩니다.
Vite 프록시가 `/api` 요청을 백엔드(`localhost:8080`)로 전달합니다.

### 빌드

```bash
# BE
cd be && ./gradlew build

# FE
cd fe && npm run build    # dist/ 에 빌드 결과물 생성
```

---

## 테스트

토스 테스트 전략(가치 중심, 파레토 법칙)을 적용하였습니다.

```bash
cd be
./gradlew test                  # 전체 테스트 실행
./gradlew test jacocoTestReport # 커버리지 리포트 포함
```

### 커버리지 현황

| 모듈 | 라인 커버리지 | 비고 |
|------|--------------|------|
| `core-api` | 83.7% | 서비스, 컨트롤러, 예외처리 |
| `db-core` | 86.7% | 어댑터 |
| `client-ai` | 9.4% | AI 호출 비용으로 의도적 제외 |

### 테스트 구성

| 분류 | 테스트 파일 | 테스트 수 |
|------|------------|----------|
| 도메인 정책 | `AiCheckServiceTest` | 14 |
| 도메인 정책 | `ProgressServiceTest` | 5 |
| 유스케이스 | `AiCheckControllerTest` | 22 |
| 유스케이스 | `ProgressControllerTest` | 2 |
| 예외처리 | `ApiControllerAdviceTest` | 5 |
| 어댑터 | `QuestProgressAdapterTest` | 5 |
| AI 평가기 | `CareerEssayEvaluatorTest` | 2 |

---

## 설정

### 주요 설정값 (`application.yml`)

| 키 | 기본값 | 설명 |
|-----|--------|------|
| `server.port` | 8080 | 서버 포트 |
| `devquest.ai.pass-score` | 70 | AI 평가 통과 점수 |
| `devquest.ai.max-retry` | 3 | 최대 재시도 횟수 |
| `devquest.ai.interview-questions` | 10 | 모의 면접 문항 수 |
| `devquest.cors.allowed-origins` | http://localhost:5173 | CORS 허용 오리진 |

### AI 설정 (`client-ai.yml`)

| 키 | 기본값 |
|-----|--------|
| `spring.ai.anthropic.chat.options.model` | claude-sonnet-4-5 |
| `spring.ai.anthropic.chat.options.max-tokens` | 2000 |
| `spring.ai.anthropic.chat.options.temperature` | 0.3 |

### 환경변수

| 변수 | 필수 | 설명 |
|------|------|------|
| `ANTHROPIC_API_KEY` | Yes | Anthropic API 키 |
