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
