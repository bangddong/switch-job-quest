import type { AiEvaluationResult, BossPackageResult, DeveloperClassResult } from '@/types/api.types'
import { OracleLoadingModal } from '@/components/ui/OracleLoadingModal'
import { AI_FORMS } from '../constants/formConfig'
import { useAiCheckForm } from '../hooks/useAiCheckForm'
import { FormField } from './FormField'

interface AiCheckFormProps {
  questId: string
  onResult: (result: AiEvaluationResult | BossPackageResult | DeveloperClassResult) => void
  initialValues?: Record<string, unknown>
  onSubmit?: (values: Record<string, unknown>) => void
}

export function AiCheckForm({ questId, onResult, initialValues, onSubmit }: AiCheckFormProps) {
  const cfg = AI_FORMS[questId]

  const { values, loading, error, activeHint, set, setListItem, setActiveHint, handleSubmit } =
    useAiCheckForm({ questId, cfg: cfg!, onResult, onSubmit, initialValues })

  if (!cfg) return null

  return (
    <div style={{ marginTop: 18, animation: 'slideIn 0.3s ease' }}>
      <div style={{ fontSize: 10, color: '#4ECDC4', letterSpacing: 4, marginBottom: 14 }}>
        🤖 AI SUBMISSION FORM
      </div>
      {cfg.description && cfg.fields.length === 0 && (
        <div
          style={{
            background: 'rgba(167,139,250,0.06)',
            border: '1px solid rgba(167,139,250,0.18)',
            borderRadius: 10,
            padding: '12px 14px',
            marginBottom: 14,
            fontSize: 13,
            color: '#94A3B8',
            lineHeight: 1.6,
          }}
        >
          {cfg.description}
        </div>
      )}
      {cfg.fields.map((f) => (
        <FormField
          key={f.key}
          field={f}
          value={values[f.key]}
          activeHint={activeHint}
          onActiveHintChange={setActiveHint}
          onChange={(val) => set(f.key, val)}
          onListItemChange={(index, val) => setListItem(f.key, index, val)}
        />
      ))}
      <OracleLoadingModal isOpen={loading} />
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
