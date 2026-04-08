import { useState } from 'react'
import type { AiEvaluationResult, BossPackageResult } from '@/types/api.types'
import { AI_FORMS } from '../constants/formConfig'
import { submitAiCheck, submitBossPackage } from '../api/aiCheckApi'
import { TechStackInput } from './TechStackInput'
import { inputStyle } from '../../../constants/styles'

interface AiCheckFormProps {
  questId: string
  onResult: (result: AiEvaluationResult | BossPackageResult) => void
  initialValues?: Record<string, unknown>
  onSubmit?: (values: Record<string, unknown>) => void
}

export function AiCheckForm({ questId, onResult, initialValues, onSubmit }: AiCheckFormProps) {
  const cfg = AI_FORMS[questId]
  const [values, setValues] = useState<Record<string, unknown>>(initialValues ?? {})
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [activeHint, setActiveHint] = useState<string | null>(null)

  if (!cfg) return null

  const set = (key: string, value: unknown) =>
    setValues((prev) => ({ ...prev, [key]: value }))

  const setListItem = (key: string, index: number, value: string) =>
    setValues((prev) => {
      const arr = [...((prev[key] as string[] | undefined) ?? [])]
      arr[index] = value
      return { ...prev, [key]: arr }
    })

  const handleSubmit = async () => {
    setLoading(true)
    setError('')
    try {
      onSubmit?.(values)
      const body = cfg.transform(values)
      const result =
        questId === '4-BOSS'
          ? await submitBossPackage(body)
          : await submitAiCheck(cfg.endpoint, body)
      onResult(result)
    } catch (e) {
      setError('서버 연결 오류: ' + (e instanceof Error ? e.message : String(e)))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ marginTop: 18, animation: 'slideIn 0.3s ease' }}>
      <div style={{ fontSize: 10, color: '#4ECDC4', letterSpacing: 4, marginBottom: 14 }}>
        🤖 AI SUBMISSION FORM
      </div>
      {cfg.fields.map((f) => (
        <div key={f.key} style={{ marginBottom: 14 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
            <label style={{ fontSize: 12, color: '#64748B' }}>{f.label}</label>
            {(f.tips || f.example) && (
              <button
                type="button"
                onClick={() => setActiveHint(activeHint === f.key ? null : f.key)}
                style={{
                  background: activeHint === f.key ? 'rgba(78,205,196,0.15)' : 'rgba(255,255,255,0.05)',
                  border: `1px solid ${activeHint === f.key ? 'rgba(78,205,196,0.4)' : 'rgba(255,255,255,0.1)'}`,
                  borderRadius: '50%',
                  width: 18,
                  height: 18,
                  fontSize: 10,
                  color: activeHint === f.key ? '#4ECDC4' : '#475569',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontFamily: "'Courier New', monospace",
                  padding: 0,
                  flexShrink: 0,
                }}
              >
                ?
              </button>
            )}
          </div>
          {activeHint === f.key && (f.tips || f.example) && (
            <div style={{
              background: 'rgba(78,205,196,0.04)',
              border: '1px solid rgba(78,205,196,0.2)',
              borderRadius: 8,
              padding: '10px 12px',
              marginBottom: 8,
              animation: 'slideIn 0.2s ease',
            }}>
              {f.tips && f.tips.length > 0 && (
                <div style={{ marginBottom: f.example ? 8 : 0 }}>
                  <div style={{ fontSize: 10, color: '#4ECDC4', letterSpacing: 2, marginBottom: 6 }}>💡 작성 팁</div>
                  {f.tips.map((tip, ti) => (
                    <div key={ti} style={{ fontSize: 12, color: '#64748B', marginBottom: 3, display: 'flex', gap: 6 }}>
                      <span style={{ color: '#4ECDC4', flexShrink: 0 }}>·</span>
                      <span>{tip}</span>
                    </div>
                  ))}
                </div>
              )}
              {f.example && (
                <div>
                  <div style={{ fontSize: 10, color: '#A78BFA', letterSpacing: 2, marginBottom: 6 }}>✏️ 예시 답변</div>
                  <div style={{ fontSize: 12, color: '#475569', lineHeight: 1.6, fontStyle: 'italic' }}>{f.example}</div>
                </div>
              )}
            </div>
          )}
          {f.type === 'text' && (
            <input
              value={(values[f.key] as string) ?? ''}
              onChange={(e) => set(f.key, e.target.value)}
              placeholder={f.placeholder}
              style={inputStyle}
            />
          )}
          {f.type === 'textarea' && (
            <textarea
              value={(values[f.key] as string) ?? ''}
              onChange={(e) => set(f.key, e.target.value)}
              placeholder={f.placeholder}
              rows={f.rows ?? 5}
              style={{ ...inputStyle, resize: 'vertical' }}
            />
          )}
          {f.type === 'list' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {Array.from({ length: f.count ?? 0 }).map((_, i) => (
                <div key={i} style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <span style={{ fontSize: 12, color: '#334155', minWidth: 16 }}>{i + 1}.</span>
                  <input
                    value={((values[f.key] as string[] | undefined) ?? [])[i] ?? ''}
                    onChange={(e) => setListItem(f.key, i, e.target.value)}
                    placeholder={f.placeholder}
                    style={{ ...inputStyle, flex: 1 }}
                  />
                </div>
              ))}
            </div>
          )}
          {f.type === 'tag-search' && (
            <TechStackInput
              value={(values[f.key] as string[] | undefined) ?? []}
              onChange={(val) => set(f.key, val)}
              placeholder={f.placeholder}
            />
          )}
        </div>
      ))}
      {error && (
        <div
          style={{
            background: 'rgba(239,68,68,0.08)',
            border: '1px solid rgba(239,68,68,0.25)',
            borderRadius: 8,
            padding: 11,
            marginBottom: 12,
            fontSize: 13,
            color: '#EF4444',
          }}
        >
          ⚠ {error}
        </div>
      )}
      <button
        onClick={handleSubmit}
        disabled={loading}
        style={{
          width: '100%',
          padding: '12px',
          background: loading
            ? 'rgba(78,205,196,0.2)'
            : 'linear-gradient(135deg, #4ECDC4, #2DD4BF)',
          border: 'none',
          borderRadius: 10,
          color: '#060610',
          fontSize: 14,
          fontWeight: 'bold',
          cursor: loading ? 'not-allowed' : 'pointer',
          fontFamily: "'Courier New', monospace",
          letterSpacing: 1,
        }}
      >
        {loading ? '⟳ AI 분석 중... (30초 소요)' : '🤖 AI 제출하기'}
      </button>
    </div>
  )
}
