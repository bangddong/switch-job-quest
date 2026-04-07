# DevQuest — 개발자 이직 RPG

개발자의 이직 준비 과정을 RPG 퀘스트로 게이미피케이션한 풀스택 애플리케이션입니다.
AI(Claude)가 에세이, 이력서, 면접 등을 평가하고, 퀘스트 클리어를 통해 이직 준비 완성도를 높여갑니다.

---

## 목차

- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [퀘스트 시스템](#퀘스트-시스템)
- [AI 평가 시스템](#ai-평가-시스템)
- [인증](#인증)
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
│   │   ├── core-domain/                #   도메인 모델, Port 인터페이스, 정책 (PassCriteriaPolicy, GradePolicy, QuestXpPolicy)
│   │   └── core-api/                   #   REST 컨트롤러, 서비스, DTO, JWT 인증 (bootJar)
│   ├── clients/
│   │   └── client-ai/                  #   Spring AI 기반 평가기 구현 (Adapter), 프롬프트 템플릿
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
        ├── app/                        #   App.tsx (루트 컴포넌트, 상태 관리)
        ├── features/
        │   ├── auth/                   #   GitHub OAuth 로그인, JWT 콜백
        │   ├── quest-map/              #   퀘스트 맵, Act 카드, 통계 패널
        │   ├── quest-detail/           #   퀘스트 상세, Act 뷰
        │   ├── ai-check/               #   AI 평가 폼, 결과 카드, 모의면접
        │   ├── interview-coach/        #   인터뷰 코치 세션
        │   ├── growth/                 #   성장 대시보드, 히스토리
        │   └── character/             #   캐릭터 생성, 온보딩
        ├── components/ui/              #   GradeTag, ProgressBar, ScoreRing
        ├── types/                      #   Quest, API 타입 정의
        ├── hooks/                      #   useAuth (JWT 관리)
        └── lib/                        #   apiClient (HTTP + JWT 자동 포함)
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
- **정책**: `PassCriteriaPolicy`, `GradePolicy`, `QuestXpPolicy` — 도메인 정책 중앙화

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

- **일반 퀘스트**: Claude Haiku 4.5 (속도 최적화)
- **BOSS 퀘스트**: Claude Sonnet 4.5 (품질 최적화, `@Qualifier("bossChatClient")`)
- **통과 기준**: 70점 이상
- **재시도**: 최대 3회 (null 응답 또는 파싱 실패 시 자동 재시도, `AiCallExecutor`)
- **프롬프트**: `client-ai/src/main/resources/prompts/*.st` — Spring AI `PromptTemplate`으로 코드와 분리

### 평가 항목별 채점 기준

#### 이직 동기 에세이 (Quest 1-2)

| 기준 | 배점 |
|------|------|
| 명확성 (Clarity) | 30점 |
| 논리성 (Logic) | 30점 |
| 동기 진정성 (Motivation) | 20점 |
| 성장 방향 (Growth Vision) | 20점 |

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

## 인증

GitHub OAuth 2.0 + Stateless JWT 방식을 사용합니다.

```
FE → GitHub OAuth → code 획득
  → POST /api/v1/auth/github { code }
  → BE: code → GitHub access_token → GitHub user info → JWT 발급
  → FE: JWT를 localStorage에 저장
  → 이후 모든 API 요청에 Authorization: Bearer {JWT} 헤더 포함
```

- JWT는 서버에 저장되지 않습니다 (stateless)
- 만료 기간: 기본 30일 (`JWT_EXPIRATION_MS`)

---

## API 명세

**Base URL**: `http://localhost:8080`

> 인증이 필요한 엔드포인트는 `Authorization: Bearer {JWT}` 헤더가 필요합니다.

### Health Check

```
GET /health
→ { success: true, result: "SUCCESS", data: "DevQuest API is running" }
```

### 인증

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/auth/github` | GitHub OAuth code → JWT 발급 |

### 퀘스트 진행도 🔒

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/v1/progress` | 전체 퀘스트 진행도 조회 |
| POST | `/api/v1/progress/complete` | 퀘스트 수동 완료 처리 |
| GET | `/api/v1/progress/history` | 전체 평가 이력 조회 |
| GET | `/api/v1/progress/history/{questId}` | 퀘스트별 이력 조회 |

### AI 평가 🔒

| Method | Endpoint | 퀘스트 |
|--------|----------|--------|
| POST | `/api/v1/ai-check/career-essay` | 1-2: 이직 동기 에세이 |
| POST | `/api/v1/ai-check/company-fit` | 1-BOSS: 개발자 클래스 판별 |
| POST | `/api/v1/ai-check/tech-blog` | 2-2: 기술 블로그 |
| POST | `/api/v1/ai-check/system-design` | 2-3: 시스템 설계 |
| GET | `/api/v1/ai-check/mock-interview/questions` | 2-BOSS: 면접 문제 생성 |
| POST | `/api/v1/ai-check/mock-interview` | 2-BOSS: 면접 답변 평가 |
| POST | `/api/v1/ai-check/jd-analysis` | 3-2: JD 역분석 |
| POST | `/api/v1/ai-check/resume` | 4-1: 이력서 검토 |
| POST | `/api/v1/ai-check/skill-assessment` | 1-1: 기술 스택 자가 진단 |
| POST | `/api/v1/ai-check/personality-interview` | 5-1: 인성 면접 |
| POST | `/api/v1/ai-check/boss-package` | 4-BOSS: 지원 패키지 완성 |
| POST | `/api/v1/ai-check/act-clear-report` | Act 클리어 보고서 |
| POST | `/api/v1/ai-check/journey-report` | 5-BOSS: 전체 여정 회고 |

### 인터뷰 코치 🔒

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/interview-coach/session/start` | 코치 세션 시작 |
| POST | `/api/v1/interview-coach/session/answer` | 답변 제출 |
| POST | `/api/v1/interview-coach/session/report` | 세션 리포트 생성 |

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
    "code": "AI_EVALUATION_FAILED",
    "message": "AI 평가에 실패했습니다"
  }
}
```

에러 코드: `INVALID_REQUEST` | `AI_EVALUATION_FAILED` | `QUEST_NOT_FOUND` | `DEFAULT`

---

## 데이터베이스

### quest_progress 테이블

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT (PK) | 자동 증가 |
| user_id | VARCHAR | 사용자 ID (github-{githubId}) |
| quest_id | VARCHAR | 퀘스트 ID (예: "1-2") |
| act_id | INT | Act 번호 (1~5) |
| status | ENUM | NOT_STARTED, IN_PROGRESS, COMPLETED, AI_FAILED |
| ai_score | INT | AI 평가 점수 (0~100) |
| earned_xp | INT | 획득 XP |
| completed_at | DATETIME | 완료 시각 |
| created_at | DATETIME | 생성 시각 (자동) |
| updated_at | DATETIME | 수정 시각 (자동) |

### quest_history 테이블

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT (PK) | 자동 증가 |
| user_id | VARCHAR | 사용자 ID |
| quest_id | VARCHAR | 퀘스트 ID |
| act_id | INT | Act 번호 |
| score | INT | 평가 점수 |
| grade | VARCHAR | 등급 (S/A/B/C/D) |
| passed | BOOLEAN | 통과 여부 |
| earned_xp | INT | 획득 XP |
| created_at | DATETIME | 평가 시각 |

> 현재 H2 인메모리 DB 사용 (서버 재시작 시 초기화)

---

## 실행 방법

### 사전 요구사항

- **JDK 21** (Gradle toolchain이 자동 감지)
- **Node.js 18+**
- **Anthropic API Key**
- **GitHub OAuth App** (Client ID / Secret)

### Backend 실행

`be/core/core-api/src/main/resources/application-local.yml` 파일 생성:

```yaml
devquest:
  auth:
    github-client-id: your-github-client-id
    github-client-secret: your-github-client-secret
    jwt-secret: your-jwt-secret-at-least-256-bits-long

spring:
  ai:
    anthropic:
      api-key: your-anthropic-api-key
```

```bash
cd be
./gradlew :core:core-api:bootRun --args='--spring.profiles.active=local'
```

서버가 `http://localhost:8080`에서 시작됩니다.

### Frontend 실행

`fe/.env.local` 파일 생성:

```env
VITE_GITHUB_CLIENT_ID=your-github-client-id
VITE_API_BASE_URL=http://localhost:8080
```

```bash
cd fe
npm install
npm run dev
```

개발 서버가 `http://localhost:5173`에서 시작됩니다.

### 빌드

```bash
# BE
cd be && ./gradlew build

# FE
cd fe && npm run build    # dist/ 에 빌드 결과물 생성
```

---

## 테스트

```bash
cd be
./gradlew test                  # 전체 테스트 실행
./gradlew test jacocoTestReport # 커버리지 리포트 포함
```

### 테스트 구성

| 분류 | 테스트 파일 | 테스트 수 |
|------|------------|----------|
| 도메인 정책 | `AiCheckServiceTest` | 14 |
| 도메인 정책 | `ProgressServiceTest` | 5 |
| 유스케이스 | `AiCheckControllerTest` | 22 |
| 유스케이스 | `ProgressControllerTest` | 2 |
| 예외처리 | `ApiControllerAdviceTest` | 5 |
| 어댑터 | `QuestProgressAdapterTest` | 5 |
| AI 평가기 | 10개 Evaluator 테스트 | 29 |

---

## 설정

### 주요 설정값 (`application.yml`)

| 키 | 기본값 | 설명 |
|-----|--------|------|
| `server.port` | 8080 | 서버 포트 |
| `devquest.ai.pass-score` | 70 | AI 평가 통과 점수 |
| `devquest.ai.max-retry` | 3 | AI 호출 최대 재시도 횟수 |
| `devquest.ai.interview-questions` | 10 | 모의 면접 문항 수 |
| `devquest.cors.allowed-origins` | http://localhost:5173 | CORS 허용 오리진 |

### AI 모델 설정

| 키 | 기본값 | 용도 |
|-----|--------|------|
| `spring.ai.anthropic.chat.options.model` | claude-haiku-4-5-20251001 | 일반 퀘스트 |
| `devquest.ai.boss-model` | claude-sonnet-4-5 | BOSS 퀘스트 |
| `spring.ai.anthropic.chat.options.max-tokens` | 2000 | 일반 최대 토큰 |
| `devquest.ai.boss-max-tokens` | 4000 | BOSS 최대 토큰 |

### 환경변수

| 변수 | 필수 | 설명 |
|------|------|------|
| `ANTHROPIC_API_KEY` | ✅ | Anthropic API 키 |
| `GITHUB_CLIENT_ID` | ✅ | GitHub OAuth App Client ID |
| `GITHUB_CLIENT_SECRET` | ✅ | GitHub OAuth App Client Secret |
| `JWT_SECRET` | ✅ | JWT 서명 키 (256비트 이상) |
| `JWT_EXPIRATION_MS` | - | JWT 만료 시간 (기본: 30일) |
| `CORS_ALLOWED_ORIGINS` | - | CORS 허용 오리진 (기본: localhost:5173) |
| `VITE_GITHUB_CLIENT_ID` | ✅ (FE) | FE GitHub OAuth Client ID |
