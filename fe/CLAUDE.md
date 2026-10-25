# FE 아키텍처 규칙 (React 19 + TypeScript + Vite)

## 컴포넌트 패턴 (금지 규칙 포함)

- **named export** 강제 — default export 사용 금지
- **인라인 스타일** (`React.CSSProperties`) — CSS 모듈, styled-components, Tailwind 사용 금지
- **상태**: `useState` only — Redux, Context, Zustand 등 외부 상태관리 사용 금지
- **데이터 흐름**: App.tsx에서 props drilling + callback lifting
- **Props 인터페이스**: `interface [Name]Props { ... }`

## 환경변수 규칙 (보안)

- 모든 설정값은 `import.meta.env.VITE_*` 사용 — 코드에 직접 하드코딩 금지
- 로컬 값은 `.env.local`에 작성 (git 무시됨) — `.env.example`에 키 이름과 설명만 추가
- `.env.local`은 커밋 금지, `.env.example`만 커밋

## 테스트 (vitest — #331 도입)

- **러너: vitest.** 설정은 `vite.config.ts`의 `test` 필드에 병합돼 있다(별도 `vitest.config.ts` 없음) —
  `resolve.alias`(`@`)와 react plugin을 그대로 재사용한다.
- **실행**: `npm test`(= `vitest run`) / 워치는 `npm run test:watch`.
  **`fe-ci.yml`의 CI 게이트라 실패하면 PR이 막힌다** — 커밋 전 로컬에서 통과시킬 것.
- **globals 미설정** → `import { describe, it, expect } from 'vitest'` **명시 import 필수**.
  안 쓰면 `tsc`에서 바로 깨진다.
- **파일 위치**: 대상과 같은 디렉토리에 `xxx.test.ts` (예: `features/resume/lib/extractPdfText.test.ts`)
- ⚠️ **`environment: 'node'`라 DOM이 없다.** 즉 **컴포넌트 테스트는 아직 불가** —
  jsdom·@testing-library 미도입(#331 범위 밖). 필요해지면 별도 chore로 도입할 것.
  **지금 테스트를 붙일 대상은 순수 함수·유틸**(정규화, 검증, 변환, 계산)이다.
- 컴포넌트를 바꿨다면 테스트 대신 `npx tsc --noEmit` + `npm run build`로 검증한다.

## Cloudflare Worker (`fe/worker/`)

- `fe/worker/` = Cloudflare Worker — 정적 자산 서빙 + `/api/*` 중계. 계획서: `docs/superpowers/plans/2026-10-24-vercel-to-cloudflare-workers.md`
- **별도 tsconfig** (`worker/tsconfig.json`, DOM 없음)이며 `npm run build`에 포함된다. lint 범위는 `src worker`.
- **`export default` 예외는 `worker/index.ts` 하나뿐** (Workers 모듈 형식). 그 외는 named export 규칙 그대로.
- 테스트는 vitest `node` 환경이라 **workerd 고유 동작은 검증되지 않는다** — workers.dev 스모크로 확인할 것.
- ⚠️ **로컬 `wrangler deploy` 금지** — 실배포다. 배포는 `fe-cd.yml`의 `deploy-workers` job만 한다.

## API 패턴

- `lib/apiClient.ts`: `callAiCheck<T>(endpoint, body, userId)` — fetch + `ApiResponse<T>` 파싱
- Feature별 래퍼: `features/[name]/api/` — 타입 안전 래퍼

## 다크 테마 컬러 팔레트

| 용도 | 색상 |
|------|------|
| 배경 | `#060610` |
| 입력 배경 | `#0A0E1A` |
| 카드 배경 | `#0F172A` |
| 테두리 | `rgba(255,255,255,0.08)` |
| 주요 텍스트 | `#F8FAFC` / `#F1F5F9` |
| 보조 텍스트 | `#475569` |
| Teal (주요 액센트) | `#4ECDC4` |
| Purple | `#A78BFA` |
| Amber/Gold | `#F59E0B` |
| Green (성공) | `#10B981` |
| Red (실패) | `#EF4444` |
| Blue (학습) | `#60A5FA` |

폰트: `'Courier New', monospace`
