---
description: FE 새 피처 모듈을 생성할 때 사용. 디렉토리 구조, 배럴 익스포트, API 래퍼, 타입 파일 패턴.
---

# FE 피처 모듈 생성 가이드

## 1. 디렉토리 구조

```
fe/src/features/[feature-name]/
  components/
    [Component].tsx
  api/
    [feature]Api.ts
  types/
    [feature].types.ts
  constants/
    [dataName].ts        (선택)
  index.ts               (배럴 익스포트)
```

## 2. 배럴 익스포트 (index.ts)

```typescript
export { ComponentA } from './components/ComponentA'
export { ComponentB } from './components/ComponentB'
export { SOME_CONSTANT } from './constants/someData'
```

규칙:
- named export만 사용 (default export 금지)
- 외부에서 사용할 것만 export

## 3. 타입 파일

**파일**: `types/[feature].types.ts`

```typescript
export interface [Feature]Props {
    // 컴포넌트 props
}

export interface [Feature]Data {
    // 데이터 구조
}
```

전역 공유 타입은 `fe/src/types/`에, 피처 전용 타입은 `features/[name]/types/`에 배치.

## 4. API 래퍼

**파일**: `api/[feature]Api.ts`

```typescript
import { callAiCheck } from '@/lib/apiClient'
import type { [ResultType] } from '@/types/api.types'

export async function submit[Feature](
    body: Record<string, unknown>,
    userId: string,
): Promise<[ResultType]> {
    return callAiCheck<[ResultType]>('[endpoint]', body, userId)
}
```

패턴:
- `callAiCheck<T>` 제네릭 클라이언트 재사용
- `@/` 경로 별칭 사용
- Promise 반환 타입 명시

## 5. App.tsx 연결

`fe/src/app/App.tsx`에서:

```typescript
import { NewComponent } from '@/features/[feature-name]'

// App 내부에서 props drilling으로 데이터 전달
<NewComponent
    prop1={stateValue}
    onAction={(result) => handleAction(result)}
/>
```

규칙:
- Context, Redux 사용 금지 — props drilling + callback lifting
- App.tsx가 single source of truth

## 검증 체크리스트

- [ ] index.ts에 배럴 익스포트 있음
- [ ] named export만 사용 (default export 없음)
- [ ] 타입 파일 위치 올바름 (전역 vs 로컬)
- [ ] API 래퍼가 callAiCheck 재사용
- [ ] App.tsx에서 props drilling으로 연결
