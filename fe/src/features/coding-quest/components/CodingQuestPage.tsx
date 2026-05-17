import { useState, useEffect } from 'react'
import type { CodingProblem, CodingSubmissionResult, CodingLevelResult } from '@/types/api.types'
import { fetchCodingProblem, submitCode, fetchCodingLevel } from '@/lib/apiClient'

const JAVA_TEMPLATE = `public class Main {
    public static void main(String[] args) {
        // 여기에 코드를 작성하세요
    }
}`

const KOTLIN_TEMPLATE = `fun main() {
    // 여기에 코드를 작성하세요
}`

type Language = 'JAVA' | 'KOTLIN'

function difficultyColor(difficulty: string): string {
  if (difficulty === 'EASY') return '#4caf50'
  if (difficulty === 'MEDIUM') return '#ff9800'
  return '#f44336'
}

export function CodingQuestPage() {
  const [language, setLanguage] = useState<Language>('JAVA')
  const [problem, setProblem] = useState<CodingProblem | null>(null)
  const [levelResult, setLevelResult] = useState<CodingLevelResult | null>(null)
  const [code, setCode] = useState<string>(JAVA_TEMPLATE)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<CodingSubmissionResult | null>(null)
  const [loadingProblem, setLoadingProblem] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadProblem = (lang: Language) => {
    setLoadingProblem(true)
    setError(null)
    setResult(null)
    fetchCodingProblem(lang)
      .then((p) => setProblem(p))
      .catch(() => setError('문제를 불러오는 데 실패했습니다.'))
      .finally(() => setLoadingProblem(false))
  }

  useEffect(() => {
    fetchCodingLevel()
      .then((l) => setLevelResult(l))
      .catch(() => { /* 레벨 조회 실패 시 무시 */ })
    loadProblem('JAVA')
  }, [])

  const handleLanguageChange = (lang: Language) => {
    setLanguage(lang)
    setCode(lang === 'JAVA' ? JAVA_TEMPLATE : KOTLIN_TEMPLATE)
    setResult(null)
    loadProblem(lang)
  }

  const handleNewProblem = () => {
    setCode(language === 'JAVA' ? JAVA_TEMPLATE : KOTLIN_TEMPLATE)
    loadProblem(language)
  }

  const handleSubmit = async () => {
    if (!problem) return
    setSubmitting(true)
    setResult(null)
    try {
      const res = await submitCode(problem.id, language, code)
      setResult(res)
    } catch {
      setError('제출 중 오류가 발생했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const levelLabel = levelResult ? `Lv.${levelResult.level}` : 'Lv.-'
  const diffLabel = problem ? problem.difficulty : '...'

  return (
    <div style={{ fontFamily: "'Courier New', monospace", color: '#F8FAFC', padding: '16px 0' }}>
      {/* 상단 바 */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: 16,
        flexWrap: 'wrap',
        gap: 8,
      }}>
        <span style={{ fontSize: 13, color: '#4ECDC4', fontWeight: 700 }}>
          {levelLabel} · <span style={{ color: difficultyColor(diffLabel) }}>{diffLabel}</span> 문제
        </span>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {(['JAVA', 'KOTLIN'] as Language[]).map((lang) => (
            <button
              key={lang}
              onClick={() => handleLanguageChange(lang)}
              style={{
                background: language === lang ? 'rgba(78,205,196,0.15)' : 'transparent',
                border: `1px solid ${language === lang ? '#4ECDC4' : 'rgba(255,255,255,0.08)'}`,
                color: language === lang ? '#4ECDC4' : '#475569',
                padding: '4px 12px',
                borderRadius: 6,
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
            style={{
              background: 'rgba(167,139,250,0.1)',
              border: '1px solid rgba(167,139,250,0.3)',
              color: '#A78BFA',
              padding: '4px 12px',
              borderRadius: 6,
              cursor: loadingProblem ? 'not-allowed' : 'pointer',
              fontSize: 12,
              fontFamily: "'Courier New', monospace",
              opacity: loadingProblem ? 0.6 : 1,
            }}
          >
            새 문제 받기
          </button>
        </div>
      </div>

      {/* 2컬럼 레이아웃 */}
      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
        {/* 왼쪽: 문제 정보 */}
        <div style={{
          flex: '0 0 45%',
          background: '#0F172A',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: 10,
          padding: 16,
          minWidth: 0,
        }}>
          {loadingProblem ? (
            <p style={{ color: '#475569', fontSize: 13, margin: 0 }}>문제 불러오는 중...</p>
          ) : error && !problem ? (
            <p style={{ color: '#EF4444', fontSize: 13, margin: 0 }}>{error}</p>
          ) : problem ? (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
                <span style={{ fontSize: 14, fontWeight: 700, color: '#F8FAFC' }}>{problem.title}</span>
                <span style={{
                  fontSize: 11,
                  color: difficultyColor(problem.difficulty),
                  background: `${difficultyColor(problem.difficulty)}20`,
                  border: `1px solid ${difficultyColor(problem.difficulty)}50`,
                  padding: '2px 7px',
                  borderRadius: 4,
                }}>
                  {problem.difficulty}
                </span>
              </div>
              <p style={{ fontSize: 13, color: '#CBD5E1', margin: '0 0 14px', lineHeight: 1.6 }}>
                {problem.description}
              </p>
              {problem.testCases.length > 0 && (
                <div>
                  <p style={{ fontSize: 11, color: '#475569', margin: '0 0 6px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>예시</p>
                  {problem.testCases.slice(0, 2).map((tc, i) => (
                    <div key={i} style={{
                      background: '#1E293B',
                      border: '1px solid rgba(255,255,255,0.06)',
                      borderRadius: 6,
                      padding: '8px 10px',
                      marginBottom: 6,
                      fontSize: 12,
                    }}>
                      <div style={{ color: '#475569', marginBottom: 2 }}>입력: <span style={{ color: '#F1F5F9' }}>{tc.input}</span></div>
                      <div style={{ color: '#475569' }}>출력: <span style={{ color: '#4ECDC4' }}>{tc.expectedOutput}</span></div>
                    </div>
                  ))}
                </div>
              )}
            </>
          ) : null}
        </div>

        {/* 오른쪽: 코드 에디터 + 제출 + 결과 */}
        <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 10 }}>
          <textarea
            value={code}
            onChange={(e) => setCode(e.target.value)}
            spellCheck={false}
            style={{
              fontFamily: 'monospace',
              whiteSpace: 'pre',
              fontSize: 14,
              width: '100%',
              minHeight: 300,
              background: '#1e1e1e',
              color: '#d4d4d4',
              padding: 12,
              border: 'none',
              resize: 'vertical',
              borderRadius: 8,
              outline: 'none',
              boxSizing: 'border-box',
            }}
          />

          <button
            onClick={handleSubmit}
            disabled={submitting || !problem}
            style={{
              background: submitting ? 'rgba(78,205,196,0.05)' : 'rgba(78,205,196,0.15)',
              border: '1px solid rgba(78,205,196,0.4)',
              color: '#4ECDC4',
              padding: '10px 0',
              borderRadius: 8,
              cursor: submitting || !problem ? 'not-allowed' : 'pointer',
              fontSize: 14,
              fontFamily: "'Courier New', monospace",
              fontWeight: 700,
              opacity: !problem ? 0.5 : 1,
              width: '100%',
            }}
          >
            {submitting ? '채점 중...' : '제출'}
          </button>

          {/* 결과 영역 */}
          {submitting && (
            <div style={{
              background: 'rgba(78,205,196,0.04)',
              border: '1px solid rgba(78,205,196,0.2)',
              borderRadius: 8,
              padding: '12px 14px',
              fontSize: 13,
              color: '#4ECDC4',
            }}>
              채점 중...
            </div>
          )}
          {result && !submitting && (
            <div style={{
              background: result.passed ? 'rgba(16,185,129,0.04)' : 'rgba(239,68,68,0.04)',
              border: `1px solid ${result.passed ? 'rgba(16,185,129,0.25)' : 'rgba(239,68,68,0.25)'}`,
              borderRadius: 8,
              padding: '12px 14px',
              fontSize: 13,
            }}>
              <p style={{ margin: '0 0 6px', color: result.passed ? '#10B981' : '#EF4444', fontWeight: 700 }}>
                {result.passed ? '✓ 통과! 모든 테스트케이스 통과' : '✗ 실패'}
              </p>
              {result.passed && result.stdout && (
                <p style={{ margin: '0 0 4px', color: '#CBD5E1', fontSize: 12 }}>
                  stdout: <code>{result.stdout}</code>
                </p>
              )}
              {!result.passed && (result.stderr || result.message) && (
                <p style={{ margin: 0, color: '#EF4444', fontSize: 12, whiteSpace: 'pre-wrap' }}>
                  {result.stderr || result.message}
                </p>
              )}
            </div>
          )}
          {error && !submitting && !result && (
            <div style={{
              background: 'rgba(239,68,68,0.04)',
              border: '1px solid rgba(239,68,68,0.25)',
              borderRadius: 8,
              padding: '10px 14px',
              fontSize: 13,
              color: '#EF4444',
            }}>
              {error}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
