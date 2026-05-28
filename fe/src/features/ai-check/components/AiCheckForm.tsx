import { useState } from 'react'
import type { AiEvaluationResult, BossPackageResult, DeveloperClassResult, JdAnalysisResult } from '@/types/api.types'
import { OracleLoadingModal } from '@/components/ui/OracleLoadingModal'
import { AI_FORMS } from '../constants/formConfig'
import { useAiCheckForm } from '../hooks/useAiCheckForm'
import { FormField } from './FormField'

interface AiCheckFormProps {
  questId: string
  onResult: (result: AiEvaluationResult | BossPackageResult | DeveloperClassResult | JdAnalysisResult) => void
  initialValues?: Record<string, unknown>
  onSubmit?: (values: Record<string, unknown>) => void
}

export function AiCheckForm({ questId, onResult, initialValues, onSubmit }: AiCheckFormProps) {
  const cfg = AI_FORMS[questId]
  const [currentStep, setCurrentStep] = useState(0)
  const [stepError, setStepError] = useState('')

  const { values, loading, error, activeHint, set, setListItem, setActiveHint, handleSubmit } =
    useAiCheckForm({ questId, cfg: cfg!, onResult, onSubmit, initialValues })

  if (!cfg) return null

  const fields = cfg.fields
  const isMultiStep = fields.length > 1

  const validateCurrentStep = (): string | null => {
    const field = fields[currentStep]
    if (!field) return null
    const val = values[field.key]
    if (field.type === 'text' || field.type === 'textarea') {
      if (!val || !(val as string).trim()) {
        return `'${field.label}' 항목을 입력해주세요`
      }
    } else if (field.type === 'list') {
      const arr = (val as string[] | undefined) ?? []
      const hasNonEmpty = arr.some((s) => (typeof s === 'string' ? s : '').trim().length > 0)
      if (!hasNonEmpty) {
        return `'${field.label}' 항목을 최소 1개 이상 입력해주세요`
      }
    } else if (field.type === 'tag-search') {
      const arr = (val as string[] | undefined) ?? []
      if (arr.length === 0) {
        return `'${field.label}' 항목을 최소 1개 이상 선택해주세요`
      }
    }
    return null
  }

  const handleNext = () => {
    const err = validateCurrentStep()
    if (err) {
      setStepError(err)
      return
    }
    setStepError('')
    setCurrentStep((s) => s + 1)
  }

  const handlePrev = () => {
    setStepError('')
    setCurrentStep((s) => s - 1)
  }

  const isLastStep = currentStep === fields.length - 1

  return (
    <div style={{ marginTop: 18, animation: 'slideIn 0.3s ease' }}>
      <div style={{ fontSize: 10, color: '#4ECDC4', letterSpacing: 4, marginBottom: 14 }}>
        AI SUBMISSION FORM
      </div>
      {cfg.description && fields.length === 0 && (
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

      {/* 진행 표시기 (멀티스텝 시만) */}
      {isMultiStep && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 6,
            marginBottom: 16,
          }}
        >
          {fields.map((_, i) => (
            <div
              key={i}
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                background: i <= currentStep ? '#4ECDC4' : 'rgba(255,255,255,0.2)',
                transition: 'background 0.2s ease',
              }}
            />
          ))}
          <span style={{ fontSize: 11, color: '#475569', marginLeft: 4 }}>
            {currentStep + 1} / {fields.length}
          </span>
        </div>
      )}

      {/* 필드 렌더링 */}
      {isMultiStep ? (
        <FormField
          key={fields[currentStep]?.key}
          field={fields[currentStep]!}
          value={values[fields[currentStep]?.key ?? '']}
          activeHint={activeHint}
          onActiveHintChange={setActiveHint}
          onChange={(val) => set(fields[currentStep]!.key, val)}
          onListItemChange={(index, val) => setListItem(fields[currentStep]!.key, index, val)}
        />
      ) : (
        fields.map((f) => (
          <FormField
            key={f.key}
            field={f}
            value={values[f.key]}
            activeHint={activeHint}
            onActiveHintChange={setActiveHint}
            onChange={(val) => set(f.key, val)}
            onListItemChange={(index, val) => setListItem(f.key, index, val)}
          />
        ))
      )}

      <OracleLoadingModal isOpen={loading} />

      {/* 스텝 오류 메시지 */}
      {stepError && (
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
          {stepError}
        </div>
      )}

      {/* 제출 오류 메시지 */}
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
          {error}
        </div>
      )}

      {/* 버튼 영역 */}
      {isMultiStep ? (
        <div style={{ display: 'flex', gap: 8 }}>
          {currentStep > 0 && (
            <button
              onClick={handlePrev}
              disabled={loading}
              style={{
                flex: 1,
                padding: '12px',
                background: 'rgba(255,255,255,0.06)',
                border: '1px solid rgba(255,255,255,0.12)',
                borderRadius: 10,
                color: '#94A3B8',
                fontSize: 14,
                cursor: 'pointer',
                fontFamily: "'Courier New', monospace",
              }}
            >
              ← 이전
            </button>
          )}
          {isLastStep ? (
            <button
              onClick={handleSubmit}
              disabled={loading}
              style={{
                flex: currentStep > 0 ? 2 : 1,
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
              {loading ? '⟳ AI 분석 중... (30초 소요)' : 'AI 제출하기'}
            </button>
          ) : (
            <button
              onClick={handleNext}
              disabled={loading}
              style={{
                flex: currentStep > 0 ? 2 : 1,
                padding: '12px',
                background: 'rgba(78,205,196,0.12)',
                border: '1px solid rgba(78,205,196,0.3)',
                borderRadius: 10,
                color: '#4ECDC4',
                fontSize: 14,
                fontWeight: 'bold',
                cursor: 'pointer',
                fontFamily: "'Courier New', monospace",
              }}
            >
              다음 →
            </button>
          )}
        </div>
      ) : (
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
          {loading ? '⟳ AI 분석 중... (30초 소요)' : 'AI 제출하기'}
        </button>
      )}
    </div>
  )
}
