---
description: FE React 컴포넌트를 생성할 때 사용. 함수형 컴포넌트, 인라인 스타일, 다크 테마 컬러, Props 인터페이스 패턴.
---

# FE 컴포넌트 생성 가이드

## 1. 기본 구조

```typescript
interface [Component]Props {
    // props 정의
}

export function [Component]({ prop1, prop2 }: [Component]Props) {
    const [state, setState] = useState<Type>(initialValue)

    return (
        <div style={{ /* 인라인 스타일 */ }}>
            {/* JSX */}
        </div>
    )
}
```

규칙:
- `export function` (named export, default export 금지)
- Props 인터페이스는 컴포넌트 바로 위에 선언
- 클래스 컴포넌트 사용 금지

## 2. 인라인 스타일 패턴

```typescript
// 재사용 스타일 객체
const containerStyle: React.CSSProperties = {
    background: '#0F172A',
    border: '1px solid rgba(255,255,255,0.08)',
    borderRadius: 12,
    padding: 20,
}

const inputStyle: React.CSSProperties = {
    width: '100%',
    background: '#0A0E1A',
    border: '1px solid rgba(255,255,255,0.08)',
    borderRadius: 8,
    padding: '9px 13px',
    color: '#F1F5F9',
    fontSize: 13,
    outline: 'none',
    boxSizing: 'border-box',
    fontFamily: "'Courier New', monospace",
    lineHeight: 1.6,
}
```

금지:
- CSS 모듈 (`.module.css`)
- styled-components / emotion
- Tailwind CSS
- 외부 UI 라이브러리 (Material-UI 등)

## 3. 다크 테마 컬러 팔레트

### 배경 & 기본

| 용도 | 색상 |
|------|------|
| 페이지 배경 | `#060610` |
| 입력 배경 | `#0A0E1A` |
| 카드 배경 | `#0F172A` |
| 깊은 배경 | `#1E293B` |
| 테두리 | `rgba(255,255,255,0.08)` |

### 텍스트

| 용도 | 색상 |
|------|------|
| 주요 텍스트 | `#F8FAFC` / `#F1F5F9` |
| 보조 텍스트 | `#475569` |
| 비활성 텍스트 | `#334155` |

### 액센트

| 용도 | 색상 | rgba 4% 배경 |
|------|------|-------------|
| Teal (주요) | `#4ECDC4` | `rgba(78,205,196,0.04)` |
| Purple | `#A78BFA` | `rgba(167,139,250,0.04)` |
| Amber/Gold | `#F59E0B` | `rgba(245,158,11,0.04)` |
| Green (성공) | `#10B981` | `rgba(16,185,129,0.04)` |
| Red (실패) | `#EF4444` | `rgba(239,68,68,0.04)` |
| Blue (학습) | `#60A5FA` | `rgba(96,165,250,0.04)` |

### 세멘틱 패턴

```typescript
// 성공 상태
{ background: 'rgba(16,185,129,0.04)', border: '1px solid rgba(16,185,129,0.25)', color: '#10B981' }

// 실패 상태
{ background: 'rgba(239,68,68,0.04)', border: '1px solid rgba(239,68,68,0.25)', color: '#EF4444' }

// 강조 배지
{ color: accentColor, background: `${accentColor}15`, border: `1px solid ${accentColor}40` }
```

## 4. 상태 관리

```typescript
// 단순 상태
const [loading, setLoading] = useState(false)
const [error, setError] = useState('')

// 딕셔너리 상태 (불변 업데이트)
const [values, setValues] = useState<Record<string, unknown>>({})
const set = (key: string, value: unknown) =>
    setValues((prev) => ({ ...prev, [key]: value }))

// 계산된 값
const computed = useCallback(() => {
    // ...
}, [dependency])
```

금지: Redux, Context API, Zustand, Jotai 등 외부 상태관리

## 5. 공유 UI 컴포넌트

위치: `fe/src/components/ui/`

사용 가능한 공유 컴포넌트:
- `ScoreRing` — 원형 점수 표시 (SVG)
- `ProgressBar` — 진행률 바
- `GradeTag` — 등급 배지 (S/A/B/C/D)

피처 전용 컴포넌트: `fe/src/features/[name]/components/`

## 검증 체크리스트

- [ ] named export 사용 (default export 없음)
- [ ] Props 인터페이스 정의
- [ ] 인라인 스타일만 사용 (CSS 파일 없음)
- [ ] 다크 테마 컬러 팔레트 준수
- [ ] 폰트: `'Courier New', monospace`
- [ ] 외부 상태관리 사용 안 함
