import { useState, useEffect } from 'react'
import Editor from '@monaco-editor/react'
import type { CodingProblem, CodingSubmissionResult, CodingLevelResult, CodingQuestState } from '@/types/api.types'
import { fetchCodingProblem, submitCode, fetchCodingLevel } from '@/lib/apiClient'
import { HintSection } from './HintSection'

const JAVA_TEMPLATE = `public class Main {
    public static void main(String[] args) {
        // 여기에 코드를 작성하세요
    }
}`

const KOTLIN_TEMPLATE = `fun main() {
    // 여기에 코드를 작성하세요
}`

type Language = 'JAVA' | 'KOTLIN'
type MobileTab = 'problem' | 'code'

function difficultyColor(difficulty: string): string {
  if (difficulty === 'EASY') return '#4caf50'
  if (difficulty === 'MEDIUM') return '#ff9800'
  return '#f44336'
}

interface CodingQuestPageProps {
  onBack: () => void
  savedState?: CodingQuestState | null
  onStateChange?: (state: CodingQuestState) => void
}

export function CodingQuestPage({ onBack, savedState, onStateChange }: CodingQuestPageProps) {
  const [language, setLanguage] = useState<Language>(savedState?.language ?? 'JAVA')
  const [problem, setProblem] = useState<CodingProblem | null>(savedState?.problem ?? null)
  const [levelResult, setLevelResult] = useState<CodingLevelResult | null>(null)
  const [code, setCode] = useState<string>(savedState?.code ?? JAVA_TEMPLATE)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<CodingSubmissionResult | null>(savedState?.result ?? null)
  const [loadingProblem, setLoadingProblem] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showResult, setShowResult] = useState(savedState?.showResult ?? false)
  const [hints, setHints] = useState<string[]>(savedState?.hints ?? [])
  const [isMobile, setIsMobile] = useState(window.innerWidth < 768)
  const [mobileTab, setMobileTab] = useState<MobileTab>('problem')

  const notifyStateChange = (patch: Partial<CodingQuestState>) => {
    if (!onStateChange) return
    onStateChange({
      language,
      problem,
      code,
      result,
      showResult,
      hints,
      ...patch,
    })
  }

  const loadProblem = (lang: Language) => {
    setLoadingProblem(true)
    setError(null)
    setProblem(null)
    setResult(null)
    setShowResult(false)
    notifyStateChange({ language: lang, problem: null, result: null, showResult: false, hints: [] })
    fetchCodingProblem(lang)
      .then((p) => {
        setProblem(p)
        setHints([])
        notifyStateChange({ language: lang, problem: p, result: null, showResult: false, hints: [] })
      })
      .catch(() => {
        setError('문제를 불러오는 데 실패했습니다.')
        notifyStateChange({ language: lang, problem: null, result: null, showResult: false, hints: [] })
      })
      .finally(() => setLoadingProblem(false))
  }

  useEffect(() => {
    fetchCodingLevel()
      .then((l) => setLevelResult(l))
      .catch(() => { /* 레벨 조회 실패 시 무시 */ })

    // 저장된 문제가 없을 때만 새 문제 로드
    if (!savedState?.problem) {
      loadProblem('JAVA')
    }
  }, [])

  useEffect(() => {
    const handleResize = () => setIsMobile(window.innerWidth < 768)
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  const handleLanguageChange = (lang: Language) => {
    const newCode = lang === 'JAVA' ? JAVA_TEMPLATE : KOTLIN_TEMPLATE
    setLanguage(lang)
    setCode(newCode)
    setResult(null)
    setShowResult(false)
    setHints([])
    loadProblem(lang)
    notifyStateChange({ language: lang, code: newCode, result: null, showResult: false, hints: [] })
  }

  const handleNewProblem = () => {
    const newCode = language === 'JAVA' ? JAVA_TEMPLATE : KOTLIN_TEMPLATE
    setCode(newCode)
    setHints([])
    loadProblem(language)
    notifyStateChange({ code: newCode, hints: [] })
  }

  const handleCodeChange = (val: string) => {
    setCode(val)
    notifyStateChange({ code: val })
  }

  const handleHintsChange = (nextHints: string[]) => {
    setHints(nextHints)
    notifyStateChange({ hints: nextHints })
  }

  const handleSubmit = async () => {
    if (!problem) return
    setSubmitting(true)
    setResult(null)
    setShowResult(true)
    if (isMobile) setMobileTab('code')
    notifyStateChange({ result: null, showResult: true })
    try {
      const res = await submitCode(problem.id, language, code)
      setResult(res)
      notifyStateChange({ result: res, showResult: true })
    } catch {
      setError('제출 중 오류가 발생했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const levelLabel = levelResult ? `Lv.${levelResult.level}` : 'Lv.-'
  const diffLabel = problem?.difficulty ?? '...'
  const monacoLang = language === 'JAVA' ? 'java' : 'kotlin'
  const fileName = language === 'JAVA' ? 'Main.java' : 'Main.kt'

  const problemPanel = (
    <div
      style={{
        background: '#0F172A',
        overflowY: 'auto',
        padding: 24,
        ...(isMobile ? { flex: 1 } : { width: '38%', flexShrink: 0, borderRight: '1px solid rgba(255,255,255,0.08)' }),
      }}
    >
      {/* 모바일에서만 제목/난이도/레벨 표시 */}
      {isMobile && problem && (
        <div style={{ marginBottom: 16 }}>
          <div style={{ marginBottom: 4 }}>
            <span style={{ fontSize: 12, color: '#475569' }}>{levelLabel}</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <span style={{ fontSize: 16, fontWeight: 700, color: '#F8FAFC' }}>{problem.title}</span>
            <span
              style={{
                fontSize: 11,
                color: difficultyColor(problem.difficulty),
                background: `${difficultyColor(problem.difficulty)}20`,
                border: `1px solid ${difficultyColor(problem.difficulty)}50`,
                padding: '2px 7px',
                borderRadius: 4,
              }}
            >
              {problem.difficulty}
            </span>
          </div>
        </div>
      )}
      {loadingProblem ? (
        <p style={{ color: '#475569', fontSize: 13, margin: 0 }}>문제 불러오는 중...</p>
      ) : error && !problem ? (
        <p style={{ color: '#EF4444', fontSize: 13, margin: 0 }}>{error}</p>
      ) : problem ? (
        <>
          {/* 데스크탑에서만 문제 패널 내 제목/레벨/난이도 표시 */}
          {!isMobile && (
            <>
              <div style={{ marginBottom: 6 }}>
                <span style={{ fontSize: 12, color: '#475569' }}>{levelLabel}</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 16 }}>
                <span style={{ fontSize: 16, fontWeight: 700, color: '#F8FAFC' }}>{problem.title}</span>
                <span
                  style={{
                    fontSize: 11,
                    color: difficultyColor(problem.difficulty),
                    background: `${difficultyColor(problem.difficulty)}20`,
                    border: `1px solid ${difficultyColor(problem.difficulty)}50`,
                    padding: '2px 7px',
                    borderRadius: 4,
                  }}
                >
                  {problem.difficulty}
                </span>
              </div>
            </>
          )}
          <p style={{ fontSize: 14, color: '#CBD5E1', margin: '0 0 20px', lineHeight: 1.7 }}>
            {problem.description}
          </p>
          {problem.testCases.length > 0 && (
            <div>
              <p style={{ fontSize: 11, color: '#475569', margin: '0 0 8px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>예시</p>
              {problem.testCases.slice(0, 2).map((tc, i) => (
                <div
                  key={i}
                  style={{
                    background: '#1E293B',
                    border: '1px solid rgba(255,255,255,0.06)',
                    borderRadius: 6,
                    padding: '10px 12px',
                    marginBottom: 8,
                    fontSize: 13,
                  }}
                >
                  <div style={{ color: '#475569', marginBottom: 4 }}>
                    입력: <span style={{ color: '#F1F5F9', fontFamily: 'monospace' }}>{tc.input}</span>
                  </div>
                  <div style={{ color: '#475569' }}>
                    출력: <span style={{ color: '#4ECDC4', fontFamily: 'monospace' }}>{tc.expectedOutput}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
          <HintSection
            problem={problem}
            initialHints={hints}
            onHintsChange={handleHintsChange}
          />
        </>
      ) : null}
    </div>
  )

  const editorPanel = (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      {/* 파일명 탭 (데스크탑에서만) */}
      {!isMobile && (
        <div
          style={{
            background: '#0A0E1A',
            borderBottom: '1px solid rgba(255,255,255,0.08)',
            padding: '0 16px',
            display: 'flex',
            alignItems: 'stretch',
          }}
        >
          <div
            style={{
              padding: '8px 16px',
              fontSize: 12,
              color: '#F8FAFC',
              background: '#1E293B',
              borderTop: '2px solid #4ECDC4',
              borderRight: '1px solid rgba(255,255,255,0.08)',
              borderLeft: '1px solid rgba(255,255,255,0.08)',
            }}
          >
            {fileName}
          </div>
        </div>
      )}

      {/* Monaco Editor */}
      <div style={{ flex: 1, overflow: 'hidden' }}>
        <Editor
          height="100%"
          language={monacoLang}
          value={code}
          onChange={(val) => handleCodeChange(val ?? '')}
          theme="vs-dark"
          options={{
            fontSize: 14,
            fontFamily: 'Consolas, "Courier New", monospace',
            minimap: { enabled: false },
            scrollBeyondLastLine: false,
            lineNumbers: 'on',
            padding: { top: 12, bottom: 12 },
            automaticLayout: true,
          }}
        />
      </div>

      {/* 결과 패널 */}
      <div
        style={{
          height: showResult ? 160 : 0,
          overflow: 'hidden',
          transition: 'height 0.25s ease',
          borderTop: showResult ? '1px solid rgba(255,255,255,0.08)' : 'none',
          background: '#0A0E1A',
          flexShrink: 0,
        }}
      >
        <div style={{ padding: '12px 20px', height: '100%', overflowY: 'auto', boxSizing: 'border-box' }}>
          {submitting && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#4ECDC4', fontSize: 13 }}>
              <span style={{ animation: 'spin 1s linear infinite' }}>⟳</span>
              채점 중...
            </div>
          )}
          {result && !submitting && (
            <div
              style={{
                background: result.passed ? 'rgba(16,185,129,0.04)' : 'rgba(239,68,68,0.04)',
                border: `1px solid ${result.passed ? 'rgba(16,185,129,0.25)' : 'rgba(239,68,68,0.25)'}`,
                borderRadius: 8,
                padding: '12px 14px',
                fontSize: 13,
              }}
            >
              <p style={{ margin: '0 0 6px', color: result.passed ? '#10B981' : '#EF4444', fontWeight: 700 }}>
                {result.passed ? '✓ 모든 테스트케이스 통과' : '✗ 실패'}
              </p>
              {result.passed && result.stdout && (
                <p style={{ margin: '0 0 4px', color: '#CBD5E1', fontSize: 12 }}>
                  stdout: <code style={{ fontFamily: 'monospace' }}>{result.stdout}</code>
                </p>
              )}
              {!result.passed && (result.stderr || result.message) && (
                <p style={{ margin: 0, color: '#EF4444', fontSize: 12, whiteSpace: 'pre-wrap', fontFamily: 'monospace' }}>
                  {result.stderr ?? result.message}
                </p>
              )}
            </div>
          )}
          {error && !submitting && !result && (
            <div
              style={{
                background: 'rgba(239,68,68,0.04)',
                border: '1px solid rgba(239,68,68,0.25)',
                borderRadius: 8,
                padding: '10px 14px',
                fontSize: 13,
                color: '#EF4444',
              }}
            >
              {error}
            </div>
          )}
        </div>
      </div>
    </div>
  )

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        display: 'flex',
        flexDirection: 'column',
        background: '#060610',
        fontFamily: "'Courier New', monospace",
        color: '#F8FAFC',
        zIndex: 1000,
      }}
    >
      {/* TopBar */}
      <div
        style={{
          height: 48,
          flexShrink: 0,
          background: '#0A0E1A',
          borderBottom: '1px solid rgba(255,255,255,0.08)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 16px',
          gap: isMobile ? 6 : 12,
        }}
      >
        {/* 좌: 뒤로가기 */}
        <button
          onClick={onBack}
          style={{
            background: 'none',
            border: 'none',
            color: '#475569',
            cursor: 'pointer',
            fontSize: isMobile ? 16 : 13,
            fontFamily: "'Courier New', monospace",
            padding: isMobile ? '4px 6px' : '4px 8px',
            borderRadius: 4,
            whiteSpace: 'nowrap',
            flexShrink: 0,
          }}
          aria-label="퀘스트 맵으로 돌아가기"
          onMouseEnter={(e) => { (e.currentTarget as HTMLButtonElement).style.color = '#F8FAFC' }}
          onMouseLeave={(e) => { (e.currentTarget as HTMLButtonElement).style.color = '#475569' }}
        >
          {isMobile ? '←' : '← 퀘스트 맵'}
        </button>

        {/* 중앙: 제목 + 난이도 + 레벨 (데스크탑에서만) */}
        {!isMobile && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flex: 1, justifyContent: 'center', minWidth: 0 }}>
            <span style={{ fontSize: 13, color: '#4ECDC4', fontWeight: 700, whiteSpace: 'nowrap' }}>{levelLabel}</span>
            {problem && (
              <>
                <span style={{ fontSize: 14, fontWeight: 700, color: '#F8FAFC', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {problem.title}
                </span>
                <span
                  style={{
                    fontSize: 11,
                    color: difficultyColor(diffLabel),
                    background: `${difficultyColor(diffLabel)}20`,
                    border: `1px solid ${difficultyColor(diffLabel)}50`,
                    padding: '2px 7px',
                    borderRadius: 4,
                    whiteSpace: 'nowrap',
                  }}
                >
                  {diffLabel}
                </span>
              </>
            )}
          </div>
        )}

        {/* 우: 언어 토글 + 새 문제 + 제출 */}
        <div style={{ display: 'flex', gap: isMobile ? 4 : 6, alignItems: 'center', flexShrink: 0 }}>
          {(['JAVA', 'KOTLIN'] as Language[]).map((lang) => (
            <button
              key={lang}
              onClick={() => handleLanguageChange(lang)}
              style={{
                background: language === lang ? 'rgba(78,205,196,0.15)' : 'transparent',
                border: `1px solid ${language === lang ? '#4ECDC4' : 'rgba(255,255,255,0.08)'}`,
                color: language === lang ? '#4ECDC4' : '#475569',
                padding: isMobile ? '4px 6px' : '4px 10px',
                borderRadius: 4,
                cursor: 'pointer',
                fontSize: 12,
                fontFamily: "'Courier New', monospace",
              }}
            >
              {lang}
            </button>
          ))}
          <button
            onClick={handleNewProblem}
            disabled={loadingProblem}
            aria-label="새 문제 불러오기"
            style={{
              background: 'rgba(167,139,250,0.1)',
              border: '1px solid rgba(167,139,250,0.3)',
              color: '#A78BFA',
              padding: isMobile ? '4px 6px' : '4px 12px',
              borderRadius: 4,
              cursor: loadingProblem ? 'not-allowed' : 'pointer',
              fontSize: 12,
              fontFamily: "'Courier New', monospace",
              opacity: loadingProblem ? 0.6 : 1,
            }}
          >
            {isMobile ? '↺' : '새 문제'}
          </button>
          <button
            onClick={handleSubmit}
            disabled={submitting || !problem}
            style={{
              background: submitting || !problem ? 'rgba(78,205,196,0.3)' : '#4ECDC4',
              border: 'none',
              color: '#060610',
              padding: isMobile ? '4px 8px' : '4px 16px',
              borderRadius: 4,
              cursor: submitting || !problem ? 'not-allowed' : 'pointer',
              fontSize: 12,
              fontFamily: "'Courier New', monospace",
              fontWeight: 700,
              opacity: !problem ? 0.5 : 1,
            }}
          >
            {submitting ? '채점 중...' : '제출'}
          </button>
        </div>
      </div>

      {/* 모바일 탭 바 */}
      {isMobile && (
        <div
          role="tablist"
          style={{
            display: 'flex',
            background: '#0A0E1A',
            borderBottom: '1px solid rgba(255,255,255,0.08)',
            flexShrink: 0,
          }}
        >
          {(['problem', 'code'] as MobileTab[]).map((tab) => {
            const label = tab === 'problem' ? '문제' : '코드'
            const isActive = mobileTab === tab
            return (
              <button
                key={tab}
                role="tab"
                aria-selected={isActive}
                onClick={() => setMobileTab(tab)}
                style={{
                  flex: 1,
                  padding: '10px 0',
                  background: 'none',
                  border: 'none',
                  borderBottom: isActive ? '2px solid #4ECDC4' : '2px solid transparent',
                  color: isActive ? '#4ECDC4' : '#475569',
                  fontSize: 13,
                  fontFamily: "'Courier New', monospace",
                  cursor: 'pointer',
                  fontWeight: isActive ? 700 : 400,
                }}
              >
                {label}
              </button>
            )
          })}
        </div>
      )}

      {/* 본문 영역 */}
      {isMobile ? (
        <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
          <div style={{ flex: 1, overflow: 'hidden', display: mobileTab === 'problem' ? 'flex' : 'none', flexDirection: 'column' }}>
            {problemPanel}
          </div>
          <div style={{ flex: 1, overflow: 'hidden', display: mobileTab === 'code' ? 'flex' : 'none', flexDirection: 'column' }}>
            {editorPanel}
          </div>
        </div>
      ) : (
        <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
          {problemPanel}
          {editorPanel}
        </div>
      )}
    </div>
  )
}
